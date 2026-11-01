# Set MAVEN_CMD, JAVA_HOME, LOCAL_WEB_SERVER, LOCAL_NODE and FFMPEG_PATH to local installed tools.
# Call through Invoke-BoundedLocalTest.ps1 after `mvn test-compile` and a standalone web build.
$ErrorActionPreference = 'Stop'
foreach ($name in @('MAVEN_CMD','JAVA_HOME','LOCAL_WEB_SERVER','LOCAL_NODE','FFMPEG_PATH')) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if (-not $value -or -not (Test-Path -LiteralPath $value)) { throw "$name must point to a local installed tool/file" }
}
$seconds = if ($env:LOCAL_TEST_SECONDS) { [int]$env:LOCAL_TEST_SECONDS } else { 180 }
$burst = if ($env:LOCAL_TEST_BURST -eq 'true') { 'true' } else { 'false' }
$env:MAVEN_OPTS = '-Xmx192m -XX:ActiveProcessorCount=2'
$arguments = @('-o','-q','-Dtest=LocalConcurrencyTest','-Dseekfactory.localLoad=true',
    "-Dseekfactory.burst=$burst", "-Dseekfactory.loadSeconds=$seconds", '-Dseekfactory.sseClients=500',
    '-Dseekfactory.httpMedia=true', "-Dseekfactory.ffmpeg=$env:FFMPEG_PATH",
    "-Dseekfactory.webServer=$env:LOCAL_WEB_SERVER", "-Dseekfactory.node=$env:LOCAL_NODE",
    '-DargLine=-Xmx640m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC','surefire:test')
& $env:MAVEN_CMD @arguments *> target/constrained-test.log
exit $LASTEXITCODE
