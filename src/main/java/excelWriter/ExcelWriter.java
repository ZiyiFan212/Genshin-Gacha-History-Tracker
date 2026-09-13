package excelWriter;

import assets.I18nManager;
import assets.IconManager;
import assets.ItemTranslator;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import storage.IOConfiguration;
import model.GachaRecord;
import utilities.AppBootstrap;
import utilities.AppConstants;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ExcelWriter {
    private ExcelWriter() {
        /* This utility class should not be instantiated */
    }

    static final String[] bannerArr = {"banner.301", "banner.400", "banner.302", "banner.500", "banner.200", "banner.100"};
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void exportExcel(Map<String, List<GachaRecord>> pair) throws IOException, ExcelExportException {
        AppBootstrap.INSTANCE.requireInitialized();

        if (pair == null || pair.isEmpty()) {
            throw new IllegalArgumentException("No gacha data to export");
        }

        Map.Entry<String, List<GachaRecord>> entry = pair.entrySet().iterator().next();
        List<GachaRecord> records = entry.getValue();
        String uid = entry.getKey();

        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Gacha record list is empty for uid: " + uid);
        }

        records.sort((a, b) -> -compareChronologically(a, b));

        Map<String, Integer> imageCache = new HashMap<>();
        try (Workbook wb = new XSSFWorkbook()) {
            XSSFFont purpleFont = (XSSFFont) wb.createFont();
            purpleFont.setColor(IndexedColors.VIOLET.getIndex());

            XSSFFont goldenFont = (XSSFFont) wb.createFont();
            goldenFont.setColor(IndexedColors.GOLD.getIndex());

            for (int i = 0; i < bannerArr.length; i++) {
                String bannerCode = AppConstants.INSTANCE.getBannerCodeList().get(i);
                Sheet sheet = wb.createSheet(I18nManager.INSTANCE.sheetName(bannerArr[i]));
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                sheet.setDefaultRowHeightInPoints(34);
                sheet.setColumnWidth(0, 6 * 256);
                sheet.setColumnWidth(1, 28 * 256);
                sheet.setColumnWidth(2, 22 * 256);
                sheet.setColumnWidth(3, 18 * 256);
                sheet.setColumnWidth(4, 10 * 256);
                sheet.setColumnWidth(5, 10 * 256);

                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue(I18nManager.INSTANCE.get("Excelheader.icon"));
                headerRow.createCell(1).setCellValue(I18nManager.INSTANCE.get("Excelheader.name"));
                headerRow.createCell(2).setCellValue(I18nManager.INSTANCE.get("Excelheader.time"));
                headerRow.createCell(3).setCellValue(I18nManager.INSTANCE.get("Excelheader.type"));
                headerRow.createCell(4).setCellValue(I18nManager.INSTANCE.get("Excelheader.rarity"));
                headerRow.createCell(5).setCellValue(I18nManager.INSTANCE.get("Excelheader.pity"));

                int rowNum = 1;
                for (GachaRecord rd : records) {
                    if (!rd.getGachaType().equals(bannerCode)) continue;
                    Row row = sheet.createRow(rowNum);
                    addIconToCell(wb, drawing, rd.getItemID(), rowNum, imageCache);
                    String localizedName = ItemTranslator.INSTANCE.get(rd.getItemID());
                    String localizedItemType = ItemTranslator.INSTANCE.get(rd.getItemType());
                    int rank = rd.getRankType();
                    if (rank != 3) {
                        row.createCell(1).setCellValue(colorfulText(rank, localizedName, purpleFont, goldenFont));
                        row.createCell(2).setCellValue(colorfulText(rank, rd.getTime(), purpleFont, goldenFont));
                        row.createCell(3).setCellValue(colorfulText(rank, localizedItemType, purpleFont, goldenFont));
                        row.createCell(4).setCellValue(colorfulText(rank, String.valueOf(rank), purpleFont, goldenFont));
                    } else {
                        row.createCell(1).setCellValue(localizedName);
                        row.createCell(2).setCellValue(rd.getTime());
                        row.createCell(3).setCellValue(localizedItemType);
                        row.createCell(4).setCellValue(String.valueOf(rank));
                    }

                    row.createCell(5).setCellValue(rowNum);
                    rowNum++;
                }
            }

            String fileName = I18nManager.INSTANCE.get("storage.exceltitle") + uid + " " + localTime() + ".xlsx";
            Path outputFile = IOConfiguration.INSTANCE.getDefault_ExportPath().resolve(fileName);
            Files.createDirectories(outputFile.getParent());
            try (FileOutputStream fileOut = new FileOutputStream(outputFile.toFile())) {
                wb.write(fileOut);
            }
        }
    }

    private static void addIconToCell(
            Workbook workbook,
            Drawing<?> drawing,
            String itemId,
            int rowNum,
            Map<String, Integer> cache
    ) throws ExcelExportException {
        try {
            int pictureIdx = -1;

            if (cache.containsKey(itemId)) {
                pictureIdx = cache.get(itemId);
            } else {
                String resourcePath = IconManager.INSTANCE.get(itemId);
                InputStream is = ExcelWriter.class.getClassLoader().getResourceAsStream(resourcePath.trim().startsWith("/") ? resourcePath.substring(1) : resourcePath);
                if (is != null) {
                    try (is) {
                        byte[] bytes = IOUtils.toByteArray(is);
                        pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
                        cache.put(itemId, pictureIdx);
                    }
                }
            }

            if (pictureIdx < 0) {
                return;
            }

            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(rowNum);
            anchor.setCol2(1);
            anchor.setRow2(rowNum + 1);
            anchor.setDx1(20000);
            anchor.setDy1(20000);
            anchor.setDx2(-20000);
            anchor.setDy2(-20000);
            drawing.createPicture(anchor, pictureIdx);
        } catch (IOException e) {
            throw new ExcelExportException("Failed to add icon for item: " + itemId, e);
        }
    }

    private static XSSFRichTextString colorfulText(int rankType, String string, XSSFFont purple, XSSFFont gold) {
        String safe = string == null ? "" : string;
        XSSFRichTextString richString = new XSSFRichTextString(safe);
        if (rankType == 4) {
            richString.applyFont(0, safe.length(), purple);
        } else if (rankType == 5) {
            richString.applyFont(0, safe.length(), gold);
        }
        return richString;
    }

    private static String localTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }

    private static LocalDateTime parseTimeSafe(String time) {
        if (time == null || time.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(time, TIME_FORMAT);
        } catch (DateTimeParseException _) {
            return LocalDateTime.MIN;
        }
    }

    private static int compareChronologically(GachaRecord a, GachaRecord b) {
        String idA = a.getRecordID();
        String idB = b.getRecordID();
        if (!idA.isBlank() && !idB.isBlank()) {
            int byId = compareRecordId(idA, idB);
            if (byId != 0) return byId;
        }
        LocalDateTime ta = parseTimeSafe(a.getTime());
        LocalDateTime tb = parseTimeSafe(b.getTime());
        return ta.compareTo(tb);
    }

    private static int compareRecordId(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null || a.isBlank()) return -1;
        if (b == null || b.isBlank()) return 1;
        try {
            long aNum = Long.parseLong(a);
            long bNum = Long.parseLong(b);
            return Long.compare(aNum, bNum);
        } catch (NumberFormatException _) {
            return a.compareTo(b);
        }
    }

    public static class ExcelExportException extends Exception {

        public ExcelExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}




