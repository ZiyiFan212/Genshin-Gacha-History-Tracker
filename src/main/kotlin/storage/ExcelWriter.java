package storage;

import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import template.GachaRecord;
import utilities.Constvar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ExcelWriter {
    private ExcelWriter() {
        /* This utility class should not be instantiated */
    }


    static final String[] bannerArr = {"banner.301", "banner.400", "banner.302", "banner.500", "banner.200", "banner.100"};

    public static void exportExcel(Map<String, List<GachaRecord>> pair, String lang) throws IOException {
        // get the data list and the UID
        List<GachaRecord> records = pair.entrySet().iterator().next().getValue();
        String uid = pair.entrySet().iterator().next().getKey();

        records.sort((a, b) -> {
            LocalDateTime ta = parseTime(a.getTime());
            LocalDateTime tb = parseTime(b.getTime());
            int byTime = tb.compareTo(ta);
            if (byTime != 0) return byTime;
            return compareId(b.getItemID(), a.getItemID());
        });

        Map<String, Integer> imageCache = new HashMap<>();
        try(Workbook wb = new XSSFWorkbook()){
            XSSFFont purpleFont = (XSSFFont) wb.createFont();
            purpleFont.setColor(IndexedColors.VIOLET.getIndex());

            XSSFFont goldenFont = (XSSFFont) wb.createFont();
            goldenFont.setColor(IndexedColors.GOLD.getIndex());

            // TODO: language manager
            for(int i = 0; i < bannerArr.length; i++) {
                Sheet sheet = wb.createSheet(GeneralMessageManager.get(bannerArr[i]));
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                sheet.setDefaultRowHeightInPoints(34);
                sheet.setColumnWidth(0, 6 * 256);
                sheet.setColumnWidth(1, 28 * 256);
                sheet.setColumnWidth(2, 22 * 256);
                sheet.setColumnWidth(3, 18 * 256);
                sheet.setColumnWidth(4, 10 * 256);
                sheet.setColumnWidth(5, 10 * 256);

                // create the header by default language
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue(GeneralMessageManager.get("header.icon"));
                headerRow.createCell(1).setCellValue(GeneralMessageManager.get("header.name"));
                headerRow.createCell(2).setCellValue(GeneralMessageManager.get("header.time"));
                headerRow.createCell(3).setCellValue(GeneralMessageManager.get("header.type"));
                headerRow.createCell(4).setCellValue(GeneralMessageManager.get("header.rarity"));
                headerRow.createCell(5).setCellValue(GeneralMessageManager.get("header.pity"));

                int rowNum = 1;
                for (GachaRecord rd : records) {
                    if(!rd.getGachaType().equals(Constvar.INSTANCE.getBannerCode().toArray()[i])) continue;
                    Row row = sheet.createRow(rowNum);
                    addIconToCell(wb, drawing, rd.getItemID(), rowNum, imageCache);

                    String localizedName = ItemTranslationManager.returnName(rd.getItemID(), lang);
                    String str = rd.getItemType(); // could be "角色" or "Character", which crashes the manager if treated as a key
                    String localizedItemType = GeneralMessageManager.getLocalizedType(str);

                    // insert data to the column
                    int rank = rd.getRankType();
                    if (rank != 3){
                        row.createCell(1).setCellValue(colorfulText(rank, localizedName, purpleFont, goldenFont)); // rich color used
                        row.createCell(2).setCellValue(colorfulText(rank, rd.getTime(), purpleFont, goldenFont));
                        row.createCell(3).setCellValue(colorfulText(rank, localizedItemType, purpleFont, goldenFont));
                        row.createCell(4).setCellValue(colorfulText(rank, String.valueOf(rank), purpleFont, goldenFont));
                    } else {
                        row.createCell(1).setCellValue(localizedName);// no rich text and color
                        row.createCell(2).setCellValue(rd.getTime());
                        row.createCell(3).setCellValue(localizedItemType);
                        row.createCell(4).setCellValue(String.valueOf(rank));
                    }


                    row.createCell(5).setCellValue(rowNum);
                    rowNum++;
                }
            }

            String fileName = GeneralMessageManager.get("storage.excel.title") + uid + " " + localTime() + ".xlsx";
            Path outputFile = Configuration.INSTANCE.getDefault_ExportPath().resolve(fileName);
            try (FileOutputStream fileOut = new FileOutputStream(outputFile.toFile())) {
                wb.write(fileOut);
            }
        }

    }


    /**
     * A static function adding the icon of the item to the worksheet, specifically on the given row.
     * @param workbook primary container for excel
     * @param drawing drawing interface for inserting icons
     * @param itemId item ID in string
     * @param rowNum number of row when the function is called
     * @param cache cache map of icon images
     */
    private static void addIconToCell(Workbook workbook, Drawing<?> drawing, String itemId, int rowNum, Map<String, Integer> cache) {
        try {
            boolean haveIcon = cache.containsKey(itemId);
            int pictureIdx = -1;

            // if the icon is used, retrieve from the map
            if (haveIcon) {
                pictureIdx = cache.get(itemId);
            } else {
                // TODO: assets manager
                String resourcePath = AssetsManager.getIconPath(itemId);
                InputStream is = ExcelWriter.class.getResourceAsStream(resourcePath);

                if (is != null) {
                    try(is){
                        byte[] bytes = IOUtils.toByteArray(is);
                        pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
                        cache.put(itemId, pictureIdx);// store the icon
                        haveIcon = true;
                    }
                }
            }

            // helpers to position and size images
            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();
            // icon stays in column 0
            anchor.setCol1(0);
            anchor.setRow1(rowNum);
            anchor.setCol2(1);
            anchor.setRow2(rowNum + 1);
            // set x-coordinate
            anchor.setDx1(20000);
            anchor.setDy1(20000);
            anchor.setDx2(-20000);
            anchor.setDy2(-20000);
            drawing.createPicture(anchor, pictureIdx);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    // Helpers:
    /** Return the colorful string based on the rank type of the item.
     *
     * @param rankType An item's rank type
     * @param string text string
     * @param purple purple font
     * @param gold golden font
     * @return A colorful string text.
     */
    private static XSSFRichTextString colorfulText(int rankType, String string, XSSFFont purple, XSSFFont gold) {
        XSSFRichTextString richString = new XSSFRichTextString(string);
        if (rankType == 4) {
            richString.applyFont(0, string.length(), purple);
        } else if (rankType == 5) {
            richString.applyFont(0, string.length(), gold);
        }
        return richString;
    }

    /**
     * A static function obtaining the local time.
     * @return A {@link LocalDateTime} object formatted with the pattern "yyyy-MM-dd_HH-mm-ss".
     */
    private static String localTime(){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }

    /**
     * A static function converting string local time to {@link LocalDateTime} object.
     * @param time input string time
     * @return A {@link LocalDateTime} object formatted with the pattern "yyyy-MM-dd_HH-mm-ss".
     */
    private static LocalDateTime parseTime(String time) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(time, df);
    }

    /**
     * A static ID comparator when two records have the exactly same timestamp.
     * @param a string ID on the left
     * @param b String ID on the right
     * @return Return an integer result of comparison.
     */
    private static int compareId(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a.length() != b.length()) return Integer.compare(a.length(), b.length());
        return a.compareTo(b);
    }
}

