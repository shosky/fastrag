# 知识生产-多媒体处理 示例数据生成脚本
# 为指定知识库生成真实文件(图片/音频/文档/视频占位)并输出对应 INSERT SQL
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$storageRoot = 'D:/Workspace/java/github/rag/fastrag/uploads'
$kbIds = @('2097978374116728833', '2097978469549727746')  # 高速演示 / 测试
$sqlPath = 'D:/Workspace/java/github/rag/fastrag/scripts/seed-media.sql'
$sb = New-Object System.Text.StringBuilder

function New-Image([string]$path, [int]$w, [int]$h, [string]$bg, [string]$fg, [string]$title, [string]$subtitle) {
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml($bg))
  $f1 = New-Object System.Drawing.Font('Microsoft YaHei', 22, [System.Drawing.FontStyle]::Bold)
  $f2 = New-Object System.Drawing.Font('Microsoft YaHei', 12)
  $size1 = $g.MeasureString($title, $f1)
  $g.DrawString($title, $f1, [System.Drawing.Brushes]::White, ($w - $size1.Width) / 2, $h / 2 - 40)
  $size2 = $g.MeasureString($subtitle, $f2)
  $g.DrawString($subtitle, $f2, [System.Drawing.Brushes]::White, ($w - $size2.Width) / 2, $h / 2 + 10)
  # 装饰边框
  $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 3)
  $g.DrawRectangle($pen, 10, 10, $w - 21, $h - 21)
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function New-Wav([string]$path, [int]$seconds, [double]$freq) {
  $rate = 8000
  $fs = [System.IO.File]::Create($path)
  $bw = New-Object System.IO.BinaryWriter($fs)
  $dataLen = $seconds * $rate * 2
  $bw.Write([System.Text.Encoding]::ASCII.GetBytes('RIFF'))
  $bw.Write([uint32]($36 + $dataLen))
  $bw.Write([System.Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
  $bw.Write([uint32]16); $bw.Write([uint16]1); $bw.Write([uint16]1)
  $bw.Write([uint32]$rate); $bw.Write([uint32]($rate * 2))
  $bw.Write([uint16]2); $bw.Write([uint16]16)
  $bw.Write([System.Text.Encoding]::ASCII.GetBytes('data'))
  $bw.Write([uint32]$dataLen)
  for ($i = 0; $i -lt $seconds * $rate; $i++) {
    $v = [int][double][Math]::Sin(2 * [Math]::PI * $freq * $i / $rate) * 8000
    $bw.Write([int16]$v)
  }
  $bw.Dispose(); $fs.Dispose()
}

foreach ($kb in $kbIds) {
  $dir = Join-Path $storageRoot $kb
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $now = (Get-Date)

  # ===== 图片 =====
  $imgs = @(
    @{ key = 'seed_img_001'; file = 'img1.png'; name = '产品整体架构图.png'; w = 640; h = 360; bg = '#2F54EB'; title = 'FastRAG 产品架构'; sub = '接入层 / 检索层 / 生成层'; desc = '产品整体架构示意图,展示接入层、检索层、生成层三层结构'; tags = '["产品","架构图"]'; ocr = 'FastRAG 产品架构 接入层/检索层/生成层'; days = 2 },
    @{ key = 'seed_img_002'; file = 'img2.png'; name = '机房实景照片.png'; w = 640; h = 360; bg = '#13C2C2'; title = 'A 区机房实景'; sub = '2026-09 拍摄'; desc = '数据中心 A 区机柜实景照片,用于机房巡检知识配图'; tags = '["机房","巡检"]'; ocr = 'A区机房实景 2026-09'; days = 5 },
    @{ key = 'seed_img_003'; file = 'img3.png'; name = '办理流程二维码.png'; w = 300; h = 300; bg = '#722ED1'; title = '扫码办理'; sub = 'ICT 业务线上办理入口'; desc = 'ICT 业务线上办理入口二维码,配套办理流程知识使用'; tags = '["二维码","办理入口"]'; ocr = '扫码办理 ICT业务线上办理入口'; days = 9 }
  )
  foreach ($m in $imgs) {
    $p = Join-Path $dir $m.file
    New-Image -path $p -w $m.w -h $m.h -bg $m.bg -title $m.title -subtitle $m.sub
    $sz = (Get-Item $p).Length
    $ts = $now.AddDays(-$m.days).ToString('yyyy-MM-dd HH:mm:ss')
    [void]$sb.AppendLine("INSERT INTO kb_media_storage (id,kb_id,media_type,name,original_name,extension,size,object_key,resolution,width,height,ocr_text,description,tags,status,created_by,created_at,source) VALUES (CONCAT('mdimg$($m.key)',LEFT(MD5(RAND()),8)),'$kb','image','$($m.name)','$($m.name)','png',$sz,'$kb/$($m.file)','$($m.w)x$($m.h)',$($m.w),$($m.h),'$($m.ocr)','$($m.desc)','$($m.tags)','completed','admin','$ts','manual');")
  }

  # ===== 音频 =====
  $auds = @(
    @{ key = 'seed_aud_001'; file = 'aud1.wav'; name = '产品功能介绍录音.wav'; sec = 8; freq = 440; dur = 45; desc = '产品核心功能介绍配音,时长约 45 秒'; tags = '["产品","介绍"]'; tr = '大家好,接下来为大家介绍 FastRAG 产品的核心功能,包括知识库管理、智能检索与机器人发布。'; days = 1 },
    @{ key = 'seed_aud_002'; file = 'aud2.wav'; name = '客服通话录音_0901.wav'; sec = 6; freq = 330; dur = 210; desc = '9 月 1 日客户咨询通话录音,已转写'; tags = '["客服","录音"]'; tr = '客户:请问企业宽带 100M 的月费是多少? 客服:100M 企业宽带月费 299 元,年付享 9 折优惠。'; days = 4 }
  )
  foreach ($m in $auds) {
    $p = Join-Path $dir $m.file
    New-Wav -path $p -seconds $m.sec -freq $m.freq
    $sz = (Get-Item $p).Length
    $ts = $now.AddDays(-$m.days).ToString('yyyy-MM-dd HH:mm:ss')
    $tr = $m.tr.Replace("'", "''")
    [void]$sb.AppendLine("INSERT INTO kb_media_storage (id,kb_id,media_type,name,original_name,extension,size,object_key,duration,transcript,description,tags,status,created_by,created_at,source) VALUES (CONCAT('mdaud$($m.key)',LEFT(MD5(RAND()),8)),'$kb','audio','$($m.name)','$($m.name)','wav',$sz,'$kb/$($m.file)',$($m.dur),'$tr','$($m.desc)','$($m.tags)','completed','admin','$ts','manual');")
  }

  # ===== 视频(占位文件,下载可用) =====
  $vids = @(
    @{ key = 'seed_vid_001'; file = 'vid1.mp4'; name = '产品操作演示视频.mp4'; dur = 180; res = '1920x1080'; desc = '机器人从创建到发布的全流程操作演示录像,时长 3 分钟'; tags = '["演示","操作指南"]'; days = 3 },
    @{ key = 'seed_vid_002'; file = 'vid2.mp4'; name = '机房巡检录像_0905.mp4'; dur = 600; res = '1280x720'; desc = '9 月 5 日 A 区机房例行巡检录像'; tags = '["机房","巡检"]'; days = 8 }
  )
  foreach ($m in $vids) {
    $p = Join-Path $dir $m.file
    $fs = [System.IO.File]::Create($p)
    $bw = New-Object System.IO.BinaryWriter($fs)
    $bw.Write((New-Object byte[] (120 * 1024)))
    $bw.Dispose(); $fs.Dispose()
    $sz = (Get-Item $p).Length
    $ts = $now.AddDays(-$m.days).ToString('yyyy-MM-dd HH:mm:ss')
    [void]$sb.AppendLine("INSERT INTO kb_media_storage (id,kb_id,media_type,name,original_name,extension,size,object_key,duration,resolution,width,height,description,tags,status,created_by,created_at,source) VALUES (CONCAT('mdvid$($m.key)',LEFT(MD5(RAND()),8)),'$kb','video','$($m.name)','$($m.name)','mp4',$sz,'$kb/$($m.file)',$($m.dur),'$($m.res)',1920,1080,'$($m.desc)','$($m.tags)','completed','admin','$ts','manual');")
  }

  # ===== 文档 =====
  $txt1 = 'FastRAG 产品白皮书 v2.1' + [char]10 + '一、产品定位:面向企业知识管理的检索增强生成平台。' + [char]10 + '二、核心能力:知识库管理、智能检索、机器人编排、发布监控。' + [char]10 + '三、典型场景:企业客服、内部培训、运维知识沉淀。'
  $txt2 = '# 常见问题 FAQ 汇总' + [char]10 + [char]10 + '1. 企业宽带 100M 月费多少? 答:299 元/月,年付 9 折。' + [char]10 + '2. ICT 业务办理需要哪些材料? 答:营业执照副本、法人身份证复印件、经办人授权书。' + [char]10 + '3. 机器人发布后多久生效? 答:发布成功后即时生效。'
  $txt3 = '施工安全规范(摘录)' + [char]10 + '1. 高空作业必须系安全带,佩戴安全帽。' + [char]10 + '2. 电力管线作业须先断电验电,做好避让标识。' + [char]10 + '3. 现场须设置围挡与警示标志,禁止无关人员进入。'
  $docs = @(
    @{ key = 'seed_doc_001'; file = 'doc1.txt'; name = '产品白皮书_v2.1.txt'; content = $txt1; desc = '产品白皮书 2.1 版全文摘录'; tags = '["白皮书","产品"]'; days = 2 },
    @{ key = 'seed_doc_002'; file = 'doc2.md'; name = '常见问题FAQ汇总.md'; content = $txt2; desc = '客服高频问题汇总,按月更新'; tags = '["FAQ","客服"]'; days = 6 },
    @{ key = 'seed_doc_003'; file = 'doc3.txt'; name = '施工安全规范_摘录.txt'; content = $txt3; desc = '施工安全规范重点条款摘录'; tags = '["安全","规范"]'; days = 11 }
  )
  foreach ($m in $docs) {
    $p = Join-Path $dir $m.file
    [System.IO.File]::WriteAllText($p, $m.content, [System.Text.Encoding]::UTF8)
    $sz = (Get-Item $p).Length
    $ext = $m.file.Split('.')[-1]
    $ts = $now.AddDays(-$m.days).ToString('yyyy-MM-dd HH:mm:ss')
    $contentEsc = $m.content.Replace("'", "''").Replace([char]10, ' ')
    [void]$sb.AppendLine("INSERT INTO kb_media_storage (id,kb_id,media_type,name,original_name,extension,size,object_key,description,tags,status,created_by,created_at,source) VALUES (CONCAT('mddoc$($m.key)',LEFT(MD5(RAND()),8)),'$kb','document','$($m.name)','$($m.name)','$ext',$sz,'$kb/$($m.file)','$($m.desc)','$($m.tags)','completed','admin','$ts','manual');")
  }
}

[System.IO.File]::WriteAllText($sqlPath, $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Output "SQL written to $sqlPath ($($sb.ToString().Split("`n").Count - 1) statements)"
