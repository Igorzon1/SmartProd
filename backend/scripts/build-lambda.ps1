# Empacota backend Spring Boot + frontend estático em backend/target/smartprod-lambda.zip (< 50 MB)
# Uso (a partir de backend/): .\scripts\build-lambda.ps1
$ErrorActionPreference = 'Stop'

$backendDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontendDir = (Resolve-Path (Join-Path $backendDir "..\frontend")).Path
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

Push-Location $backendDir
try {
  # 1. Compila o backend (alvo Java 21)
  & .\mvnw.cmd -B test-compile
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # 2. Prepara diretório de staging
  $pkgRaw = Join-Path $backendDir "target\lambda-pkg-raw"
  $pkgDir = Join-Path $backendDir "target\lambda-pkg"
  foreach ($d in @($pkgRaw, $pkgDir)) {
    if (Test-Path $d) { Remove-Item -Recurse -Force $d }
    New-Item -ItemType Directory -Force -Path "$d\lib" | Out-Null
  }

  # 3. Copia dependências runtime excluindo Tomcat, H2 e SpringDoc/Swagger (desativado em prod)
  $excludes = @(
    "tomcat-embed-core","tomcat-embed-el","tomcat-embed-websocket","spring-boot-starter-tomcat",
    "h2",
    "springdoc-openapi-starter-webmvc-ui","springdoc-openapi-starter-webmvc-api","springdoc-openapi-starter-common",
    "swagger-ui","swagger-core-jakarta","swagger-models-jakarta","swagger-annotations-jakarta",
    "webjars-locator-lite","commons-lang3"
  ) -join ","

  & .\mvnw.cmd -B dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$pkgRaw\lib" "-DexcludeArtifactIds=$excludes"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  # 4. Copia classes compiladas e recursos do backend
  Copy-Item -Path (Join-Path $backendDir "target\classes\*") -Destination $pkgDir -Recurse -Force

  # 5. Embute frontend estático (sem README.md nem pastas mock/) apontando para a mesma origem
  $staticDir = Join-Path $pkgDir "static"
  New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
  Get-ChildItem -Path $frontendDir -Recurse -File | Where-Object {
    $_.Name -ne "README.md" -and $_.FullName -notmatch '[\\/]mock[\\/]'
  } | ForEach-Object {
    $rel = $_.FullName.Substring($frontendDir.Length + 1)
    $dest = Join-Path $staticDir $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    Copy-Item -Path $_.FullName -Destination $dest -Force
  }

  # Ajusta apiUrl para mesma origem e timeout para 45 s (cold start + auto-pause Aurora) sem BOM UTF-8
  $cfgPath = Join-Path $staticDir "js\core\config.js"
  $cfgText = [System.IO.File]::ReadAllText($cfgPath, $utf8NoBom) -replace 'apiUrl:\s*"http://localhost:8080"', 'apiUrl: ""'
  [System.IO.File]::WriteAllText($cfgPath, $cfgText, $utf8NoBom)

  $apiPath = Join-Path $staticDir "js\core\api.js"
  $apiText = [System.IO.File]::ReadAllText($apiPath, $utf8NoBom) -replace 'AbortSignal\.timeout\(15000\)', 'AbortSignal.timeout(45000)'
  [System.IO.File]::WriteAllText($apiPath, $apiText, $utf8NoBom)

  # 6. Reempacota JARs em modo STORED para melhor compressão no ZIP final (< 50 MB) e classloading mais rápido
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem

  foreach ($jar in Get-ChildItem "$pkgRaw\lib\*.jar") {
    $outJar = Join-Path "$pkgDir\lib" $jar.Name
    $inZip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    $outZip = [System.IO.Compression.ZipFile]::Open($outJar, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
      foreach ($entry in $inZip.Entries) {
        if ($entry.FullName.EndsWith("/")) { continue }
        $newEntry = $outZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::NoCompression)
        $inStream = $entry.Open()
        $outStream = $newEntry.Open()
        try { $inStream.CopyTo($outStream) } finally { $inStream.Dispose(); $outStream.Dispose() }
      }
    } finally {
      $inZip.Dispose()
      $outZip.Dispose()
    }
  }

  # 7. Gera smartprod-lambda.zip com separadores '/' compatíveis com AWS Lambda Linux
  $zipPath = Join-Path $backendDir "target\smartprod-lambda.zip"
  if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
  $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    foreach ($f in Get-ChildItem -Path $pkgDir -Recurse -File) {
      $rel = $f.FullName.Substring($pkgDir.Length + 1).Replace('\', '/')
      [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $f.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal
      ) | Out-Null
    }
  } finally {
    $zip.Dispose()
  }

  Write-Output ("Pacote gerado: {0} ({1:N2} MB)" -f $zipPath, ((Get-Item $zipPath).Length / 1MB))
} finally {
  Pop-Location
}
