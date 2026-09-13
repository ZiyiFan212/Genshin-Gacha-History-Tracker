# Step 1: Build name->ID map from translator.json
$translator = Get-Content "src/main/resources/gacha-assets/items/translator.json" -Raw | ConvertFrom-Json
$nameToId = @{}
foreach ($prop in $translator.PSObject.Properties) {
    if ($prop.Name -notin @("character", "weapon")) {
        $id = $prop.Name
        $zh = $prop.Value.zh
        $en = $prop.Value.en
        $nameToId[$zh] = $id
        $nameToId[$en] = $id
    }
}

# Step 2: Version start dates (epoch ms for 00:00 UTC+8)
# Using Asia/Shanghai timezone
$versionDates = @{
    "1.0" = [DateTimeOffset]::new(2020, 9, 15, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.1" = [DateTimeOffset]::new(2020, 11, 11, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.2" = [DateTimeOffset]::new(2020, 12, 23, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.3" = [DateTimeOffset]::new(2021, 2, 3, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.4" = [DateTimeOffset]::new(2021, 3, 17, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.5" = [DateTimeOffset]::new(2021, 4, 28, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "1.6" = [DateTimeOffset]::new(2021, 6, 9, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.0" = [DateTimeOffset]::new(2021, 7, 21, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.1" = [DateTimeOffset]::new(2021, 9, 1, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.2" = [DateTimeOffset]::new(2021, 10, 13, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.3" = [DateTimeOffset]::new(2021, 11, 24, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.4" = [DateTimeOffset]::new(2022, 1, 5, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.5" = [DateTimeOffset]::new(2022, 2, 16, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.6" = [DateTimeOffset]::new(2022, 3, 30, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.7" = [DateTimeOffset]::new(2022, 5, 31, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "2.8" = [DateTimeOffset]::new(2022, 7, 13, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.0" = [DateTimeOffset]::new(2022, 8, 24, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.1" = [DateTimeOffset]::new(2022, 9, 28, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.2" = [DateTimeOffset]::new(2022, 11, 2, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.3" = [DateTimeOffset]::new(2022, 12, 7, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.4" = [DateTimeOffset]::new(2023, 1, 18, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.5" = [DateTimeOffset]::new(2023, 3, 1, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.6" = [DateTimeOffset]::new(2023, 4, 12, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.7" = [DateTimeOffset]::new(2023, 5, 24, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "3.8" = [DateTimeOffset]::new(2023, 7, 5, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.0" = [DateTimeOffset]::new(2023, 8, 16, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.1" = [DateTimeOffset]::new(2023, 9, 27, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.2" = [DateTimeOffset]::new(2023, 11, 8, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.3" = [DateTimeOffset]::new(2023, 12, 20, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.4" = [DateTimeOffset]::new(2024, 1, 31, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.5" = [DateTimeOffset]::new(2024, 3, 13, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.6" = [DateTimeOffset]::new(2024, 4, 24, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.7" = [DateTimeOffset]::new(2024, 6, 5, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "4.8" = [DateTimeOffset]::new(2024, 7, 17, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.0" = [DateTimeOffset]::new(2024, 8, 28, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.1" = [DateTimeOffset]::new(2024, 10, 9, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.2" = [DateTimeOffset]::new(2024, 11, 20, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.3" = [DateTimeOffset]::new(2025, 1, 1, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.4" = [DateTimeOffset]::new(2025, 2, 12, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.5" = [DateTimeOffset]::new(2025, 3, 26, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.6" = [DateTimeOffset]::new(2025, 5, 27, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.7" = [DateTimeOffset]::new(2025, 7, 8, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "5.8" = [DateTimeOffset]::new(2025, 8, 19, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.0" = [DateTimeOffset]::new(2025, 9, 10, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.1" = [DateTimeOffset]::new(2025, 10, 22, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.2" = [DateTimeOffset]::new(2025, 12, 3, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.3" = [DateTimeOffset]::new(2026, 1, 14, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.4" = [DateTimeOffset]::new(2026, 2, 25, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.5" = [DateTimeOffset]::new(2026, 4, 8, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.6" = [DateTimeOffset]::new(2026, 5, 27, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
    "6.7" = [DateTimeOffset]::new(2026, 7, 1, 0, 0, 0, [TimeSpan]::FromHours(8)).ToUnixTimeMilliseconds()
}

# Step 3: Define all first-UP items with their version
# Format: @("zhName", "version")
$firstUp = @(
    # 1.0
    @("温迪", "1.0"), @("风鹰剑", "1.0"), @("阿莫斯之弓", "1.0"),
    @("可莉", "1.0"), @("四风原典", "1.0"), @("狼的末路", "1.0"),
    # 1.1
    @("达达利亚", "1.1"), @("天空之翼", "1.1"), @("尘世之锁", "1.1"),
    @("钟离", "1.1"), @("贯虹之槊", "1.1"), @("无工之剑", "1.1"),
    # 1.2
    @("阿贝多", "1.2"), @("斫峰之刃", "1.2"), @("天空之卷", "1.2"),
    @("甘雨", "1.2"), @("天空之傲", "1.2"),
    # 1.3
    @("魈", "1.3"), @("和璞鸢", "1.3"), @("磐岩结绿", "1.3"),
    @("胡桃", "1.3"), @("护摩之杖", "1.3"),
    # 1.5
    @("优菈", "1.5"), @("松籁响起之时", "1.5"),
    # 1.6
    @("枫原万叶", "1.6"), @("苍古自由之誓", "1.6"),
    # 2.0
    @("神里绫华", "2.0"), @("雾切之回光", "2.0"),
    @("宵宫", "2.0"), @("飞雷之弦振", "2.0"),
    # 2.1
    @("雷电将军", "2.1"), @("薙草之稻光", "2.1"),
    @("珊瑚宫心海", "2.1"), @("不灭月华", "2.1"),
    # 2.3
    @("荒泷一斗", "2.3"), @("赤角石溃杵", "2.3"),
    # 2.4
    @("申鹤", "2.4"), @("息灾", "2.4"),
    # 2.5
    @("八重神子", "2.5"), @("神乐之真意", "2.5"),
    # 2.6
    @("神里绫人", "2.6"), @("波乱月白经津", "2.6"),
    # 2.7
    @("夜兰", "2.7"), @("若水", "2.7"),
    # 3.0
    @("提纳里", "3.0"), @("猎人之径", "3.0"),
    # 3.1
    @("赛诺", "3.1"), @("赤沙之杖", "3.1"),
    @("妮露", "3.1"), @("圣显之钥", "3.1"),
    # 3.2
    @("纳西妲", "3.2"), @("千夜浮梦", "3.2"),
    # 3.3
    @("流浪者", "3.3"), @("图莱杜拉的回忆", "3.3"),
    # 3.4
    @("艾尔海森", "3.4"), @("裁叶萃光", "3.4"),
    # 3.5
    @("迪希雅", "3.5"), @("苇海信标", "3.5"),
    # 3.6
    @("白术", "3.6"), @("碧落之珑", "3.6"),
    # 4.0
    @("林尼", "4.0"), @("最初的大魔术", "4.0"),
    # 4.1
    @("那维莱特", "4.1"), @("万世流涌大典", "4.1"),
    @("莱欧斯利", "4.1"), @("金流监督", "4.1"),
    # 4.2
    @("芙宁娜", "4.2"), @("静水流涌之辉", "4.2"),
    # 4.3
    @("娜维娅", "4.3"), @("裁断", "4.3"),
    # 4.4
    @("闲云", "4.4"), @("鹤鸣余音", "4.4"),
    # 4.5
    @("千织", "4.5"), @("有乐御簾切", "4.5"),
    # 4.6
    @("阿蕾奇诺", "4.6"), @("赤月之形", "4.6"),
    # 4.7
    @("克洛琳德", "4.7"), @("赦罪", "4.7"),
    @("希格雯", "4.7"), @("白雨心弦", "4.7"),
    # 4.8
    @("艾梅莉埃", "4.8"), @("柔灯挽歌", "4.8"),
    # 5.0
    @("玛拉妮", "5.0"), @("冲浪时光", "5.0"),
    @("基尼奇", "5.0"), @("山王长牙", "5.0"),
    # 5.1
    @("希诺宁", "5.1"), @("岩峰巡歌", "5.1"),
    # 5.2
    @("恰斯卡", "5.2"), @("星鹫赤羽", "5.2"),
    # 5.3
    @("玛薇卡", "5.3"), @("焚曜千阳", "5.3"),
    @("茜特菈莉", "5.3"), @("祭星者之望", "5.3"),
    # 5.4
    @("梦见月瑞希", "5.4"), @("寝正月初晴", "5.4"),
    # 5.5
    @("瓦雷莎", "5.5"), @("溢彩心念", "5.5"),
    # 5.6
    @("爱可菲", "5.6"), @("香韵奏者", "5.6"),
    # 5.7
    @("丝柯克", "5.7"), @("苍耀", "5.7"),
    # 5.8
    @("伊涅芙", "5.8"), @("支离轮光", "5.8"),
    # 6.0
    @("菈乌玛", "6.0"), @("纺夜天镜", "6.0"),
    @("菲林斯", "6.0"), @("血染荒城", "6.0"),
    # 6.1
    @("奈芙尔", "6.1"), @("真语秘匣", "6.1"),
    # 6.2
    @("杜林", "6.2"), @("黑蚀", "6.2"),
    # 6.3
    @("哥伦比娅", "6.3"), @("帷间夜曲", "6.3"),
    @("兹白", "6.3"), @("朏魄含光", "6.3"),
    # 6.4
    @("法尔伽", "6.4"), @("狼的武功歌", "6.4"),
    # 6.5
    @("莉奈娅", "6.5"), @("霜结的誓金枝", "6.5")
)

# Step 4: Generate upTime.json
$upTime = @{}
$unmatched = @()

foreach ($item in $firstUp) {
    $zhName = $item[0]
    $ver = $item[1]
    $id = $nameToId[$zhName]
    if ($id) {
        $ms = $versionDates[$ver]
        if (-not $upTime.ContainsKey($id)) {
            $upTime[$id] = @{ duration = $ms }
        }
    } else {
        $unmatched += $zhName
    }
}

# Build the JSON output manually for precise control
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("{")
$entries = $upTime.GetEnumerator() | Sort-Object Name
$count = 0
foreach ($entry in $entries) {
    $count++
    $comma = if ($count -lt $entries.Count) { "," } else { "" }
    [void]$sb.AppendLine("  `"$($entry.Key)`": { `"duration`": $($entry.Value.duration) }$comma")
}
[void]$sb.AppendLine("}")

Set-Content -Path "src/main/resources/gacha-assets/upTime.json" -Value $sb.ToString() -Encoding UTF8

Write-Output "Generated upTime.json with $($upTime.Count) items"
if ($unmatched.Count -gt 0) {
    Write-Output "WARNING: Unmatched items (not in translator.json):"
    $unmatched | ForEach-Object { Write-Output "  - $_" }
}

# Also generate the full upTime.json with ALL items (keep null for non-first-UP items)
$allIds = $translator.PSObject.Properties.Name | Where-Object { $_ -notin @("character", "weapon") }
$sb2 = [System.Text.StringBuilder]::new()
[void]$sb2.AppendLine("{")
$count2 = 0
foreach ($id in $allIds) {
    $count2++
    $comma = if ($count2 -lt $allIds.Count) { "," } else { "" }
    if ($upTime.ContainsKey($id)) {
        [void]$sb2.AppendLine("  `"$id`": { `"duration`": $($upTime[$id].duration) }$comma")
    } else {
        [void]$sb2.AppendLine("  `"$id`": { `"duration`": null }$comma")
    }
}
[void]$sb2.AppendLine("}")
Set-Content -Path "src/main/resources/gacha-assets/upTime.json" -Value $sb2.ToString() -Encoding UTF8
Write-Output "Final upTime.json: $($allIds.Count) items total, $($upTime.Count) with dates"