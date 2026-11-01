param(
    [Parameter(Mandatory)][string]$CommandScript,
    [int]$MemoryMiB = 2048,
    [int]$CpuEquivalent = 1,
    [string]$Report = 'target/windows-job-results.json'
)
$ErrorActionPreference = 'Stop'
# Windows aggregate committed-memory and CPU-cycle limits. No machine-wide settings are changed.
Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
public static class BoundedLocalJob {
 [StructLayout(LayoutKind.Sequential)] struct Basic { public long User,Job; public uint Flags; public UIntPtr Min,Max; public uint Active; public UIntPtr Affinity; public uint Priority,Scheduling; }
 [StructLayout(LayoutKind.Sequential)] struct IO { public ulong ReadOps,WriteOps,OtherOps,ReadBytes,WriteBytes,OtherBytes; }
 [StructLayout(LayoutKind.Sequential)] struct Extended { public Basic Basic; public IO IO; public UIntPtr ProcessMemory,JobMemory,PeakProcess,PeakJob; }
 [StructLayout(LayoutKind.Sequential)] struct CPU { public uint Flags,Rate; }
 [StructLayout(LayoutKind.Sequential)] struct Accounting { public long User,Kernel,PeriodUser,PeriodKernel; public uint Faults,TotalProcesses,Active,Terminated; }
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr CreateJobObject(IntPtr attr,string name);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetInformationJobObject(IntPtr job,int type,IntPtr data,uint length);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool QueryInformationJobObject(IntPtr job,int type,IntPtr data,uint length,IntPtr ret);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr job,IntPtr process);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 [DllImport("kernel32.dll")] static extern IntPtr GetCurrentProcess();
 static void Set<T>(IntPtr job,int type,T value) where T:struct {
   int size=Marshal.SizeOf<T>(); IntPtr data=Marshal.AllocHGlobal(size);
   try { Marshal.StructureToPtr(value,data,false); if(!SetInformationJobObject(job,type,data,(uint)size)) throw new Win32Exception(Marshal.GetLastWin32Error()); }
   finally {Marshal.FreeHGlobal(data);}
 }
 static T Get<T>(IntPtr job,int type) where T:struct {
   int size=Marshal.SizeOf<T>(); IntPtr data=Marshal.AllocHGlobal(size);
   try {if(!QueryInformationJobObject(job,type,data,(uint)size,IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error()); return Marshal.PtrToStructure<T>(data);}
   finally {Marshal.FreeHGlobal(data);}
 }
 public static IntPtr AttachCurrent(long bytes,uint rate) {
   IntPtr job=CreateJobObject(IntPtr.Zero,null);
   if(job==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
   try {
     var limits=new Extended(); limits.Basic.Flags=0x200; limits.JobMemory=(UIntPtr)(ulong)bytes; Set(job,9,limits);
     Set(job,15,new CPU {Flags=1|4,Rate=rate});
     if(!AssignProcessToJobObject(job,GetCurrentProcess())) throw new Win32Exception(Marshal.GetLastWin32Error());
     return job;
   } catch {CloseHandle(job); throw;}
 }
 public static string Report(IntPtr job,int exit,long bytes,uint rate,double elapsed) {
   var memory=Get<Extended>(job,9); var accounting=Get<Accounting>(job,1);
   return "{\"exitCode\":"+exit+",\"memoryLimitBytes\":"+bytes+",\"cpuRatePer10000\":"+rate+",\"peakJobCommitBytes\":"+memory.PeakJob.ToUInt64()+",\"cpuSeconds\":"+((accounting.User+accounting.Kernel)/10000000.0).ToString(System.Globalization.CultureInfo.InvariantCulture)+",\"elapsedSeconds\":"+elapsed.ToString(System.Globalization.CultureInfo.InvariantCulture)+",\"totalProcesses\":"+accounting.TotalProcesses+"}";
 }
 public static void Release(IntPtr job) {CloseHandle(job);}
}
'@
$command = (Resolve-Path -LiteralPath $CommandScript).Path
$cpuRate = [Math]::Min(10000, [Math]::Max(1,[int](10000*$CpuEquivalent/[Environment]::ProcessorCount)))
$clock = [Diagnostics.Stopwatch]::StartNew()
$job = [BoundedLocalJob]::AttachCurrent(([long]$MemoryMiB*1MB),$cpuRate)
$code = 1
try {
    & $command
    $code = $LASTEXITCODE
} finally {
    $result = [BoundedLocalJob]::Report($job,$code,([long]$MemoryMiB*1MB),$cpuRate,$clock.Elapsed.TotalSeconds)
    $result | Set-Content -LiteralPath $Report
    $result
    [BoundedLocalJob]::Release($job)
}
exit $code
