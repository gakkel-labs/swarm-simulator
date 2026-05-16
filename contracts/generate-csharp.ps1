# Generates C# stubs from swarm_observer.proto.
# Run from repo root: .\contracts\generate-csharp.ps1

$RepoRoot   = Split-Path $PSScriptRoot -Parent
$ToolsDir   = "$RepoRoot\unity-client\Packages\Grpc.Tools.2.80.0\tools\windows_x64"
$ProtoDir   = "$RepoRoot\contracts\src\main\proto"
$OutDir     = "$RepoRoot\unity-client\Assets\Scripts\Grpc\Generated"
$Protoc     = "$ToolsDir\protoc.exe"
$GrpcPlugin = "$ToolsDir\grpc_csharp_plugin.exe"
$ProtoFile  = "$ProtoDir\swarm_observer.proto"
$pluginArg  = "--plugin=protoc-gen-grpc=$GrpcPlugin"

if (-not (Test-Path $Protoc)) {
    Write-Error "protoc not found at $Protoc -- restore Grpc.Tools via NuGetForUnity first."
    exit 1
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Host "Generating C# stubs into $OutDir ..."

& $Protoc --proto_path=$ProtoDir --csharp_out=$OutDir --grpc_out=$OutDir $pluginArg $ProtoFile

if ($LASTEXITCODE -ne 0) {
    Write-Error "protoc failed (exit $LASTEXITCODE)"
    exit $LASTEXITCODE
}

Write-Host "Done. Generated files:"
Get-ChildItem $OutDir -Filter "*.cs" | ForEach-Object { Write-Host "  $($_.Name)" }
