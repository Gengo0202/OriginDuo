$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}
$src="$root/work/overlaywindow/src"; $classes="$root/work/overlaywindow/classes"

# ---------------------------------------------------------------------------
# The glyph window is drawn over whatever app is on screen, and the platform
# reads a window drawn over an app as a possible tap-jacking overlay: touches
# that land underneath are marked obscured, and an app that checks for that
# mark (LSPosed's permission prompt is the one that was reported) refuses to
# answer. PRIVATE_FLAG_TRUSTED_OVERLAY is how a window says it is not that
# kind of overlay. WindowManagerService turns the bit into InputConfig's
# TRUSTED_OVERLAY (WindowManagerService.java:8819), and WindowState accepts it
# only from a session holding INTERNAL_SYSTEM_WINDOW (WindowState.java:1199) -
# which the system UI has.
#
# Two hidden names, so two reflections, and the platform may have neither.
# That is what this probe locks: it runs the real methods against a stub
# LayoutParams in three shapes - both names, no constant, no field - and pins
# the source shape that the desktop cannot observe.
# ---------------------------------------------------------------------------

$s=[System.IO.File]::ReadAllText("$root/app/src/main/java/io/github/yixing233/hyperduo/TrioOverlay.java")

$field=[regex]::Match($s,'(?s)private static int sTrustedOverlay;').Value
if(!$field){throw 'Production sTrustedOverlay missing'}
$mark=[regex]::Match($s,'(?s)private static void markTrustedOverlay\(WindowManager\.LayoutParams params\) \{.*?\n    \}').Value
if(!$mark){throw 'Production markTrustedOverlay missing'}
$flagFn=[regex]::Match($s,'(?s)private static int trustedOverlayFlag\(\) \{.*?\n    \}').Value
if(!$flagFn){throw 'Production trustedOverlayFlag missing'}
$flagsOf=[regex]::Match($s,'(?s)private static int privateFlagsOf\(WindowManager\.LayoutParams params\) \{.*?\n    \}').Value
if(!$flagsOf){throw 'Production privateFlagsOf missing'}

# The flag and the field it lives in are both @hide, so neither is in the SDK
# stub and both must be reached by name. Written directly they would not
# compile against .tools/sdk/platforms/android-37.0/android.jar.
if($s -notmatch 'getField\("PRIVATE_FLAG_TRUSTED_OVERLAY"\)'){throw 'The trusted-overlay flag is no longer read by name'}
if($s -notmatch 'getField\("privateFlags"\)'){throw 'privateFlags is no longer read by name'}
if($s -match 'params\.privateFlags'){throw 'privateFlags is written directly, which does not compile against this SDK'}
# OR, never assign: the platform owns the rest of that word.
if($s -notmatch 'field\.getInt\(params\) \| trusted'){throw 'The platform-owned private flags would be clobbered'}
if($s -notmatch 'if \(trusted == 0\) \{'){throw 'A platform without the flag must be left alone'}
# A window that keeps its public flags is what FLAG_NOT_TOUCHABLE buys.
if($s -notmatch 'FLAG_NOT_TOUCHABLE'){throw 'The window is no longer declared untouchable'}
# Marked after the public flags are set, in the constructor, where the
# parameters are still being filled in.
$flagsAt=$s.IndexOf('params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE')
$markAt=$s.IndexOf('markTrustedOverlay(params);')
if($flagsAt -lt 0 -or $markAt -lt 0 -or $markAt -lt $flagsAt){throw 'The mark is not applied with the window flags'}
# The type this window picks is deliberately the one the platform does not
# already trust by type - see the note on windowType().
if($s -notmatch 'getField\("TYPE_STATUS_BAR_SUB_PANEL"\)'){throw 'The sub-panel window type is no longer tried first'}

New-Item -ItemType Directory -Force $src | Out-Null
New-Item -ItemType Directory -Force $classes | Out-Null
# Both trees are rebuilt whole: a .java left by an earlier run would still
# compile and could shadow the one this run means to test.
Remove-Item -Recurse -Force $src, $classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $src | Out-Null
New-Item -ItemType Directory -Force $classes | Out-Null

$test=@"
import java.lang.reflect.Field;
import android.view.WindowManager;

public class OverlayTest {
$field
$mark
$flagFn
$flagsOf

 static int checks;
 static void ok(String what,boolean cond){if(!cond)throw new AssertionError(what);checks++;}
 static void eq(String what,long got,long want){if(got!=want)throw new AssertionError(what+": got <"+got+"> want <"+want+">");checks++;}

 static boolean hasField(){try{WindowManager.LayoutParams.class.getField("privateFlags");return true;}catch(Throwable t){return false;}}
 static boolean hasFlag(){try{WindowManager.LayoutParams.class.getField("PRIVATE_FLAG_TRUSTED_OVERLAY");return true;}catch(Throwable t){return false;}}
 static int peek(WindowManager.LayoutParams p)throws Exception{return WindowManager.LayoutParams.class.getField("privateFlags").getInt(p);}
 static void poke(WindowManager.LayoutParams p,int v)throws Exception{WindowManager.LayoutParams.class.getField("privateFlags").setInt(p,v);}
 static int flagValue()throws Exception{return WindowManager.LayoutParams.class.getField("PRIVATE_FLAG_TRUSTED_OVERLAY").getInt(null);}

 public static void main(String[] args)throws Exception{
  final boolean field=hasField(), flag=hasFlag();
  System.out.println("tree: field="+field+" flag="+flag);
  final int owned=0x08;

  if(flag){
   final int mark=flagValue();
   ok("the stub marks a bit the platform does not own by default",(mark&owned)==0);
   WindowManager.LayoutParams p=new WindowManager.LayoutParams();
   poke(p,owned);
   eq("the flag reads back what the platform declares",trustedOverlayFlag(),mark);
   markTrustedOverlay(p);
   eq("the mark is set",peek(p)&mark,mark);
   eq("the mark is added to the word, not assigned over it",peek(p),owned|mark);
   eq("privateFlagsOf reports the live word",privateFlagsOf(p),owned|mark);
   markTrustedOverlay(p);
   eq("marking an already marked window changes nothing",peek(p),owned|mark);
   WindowManager.LayoutParams q=new WindowManager.LayoutParams();
   poke(q,0);
   markTrustedOverlay(q);
   eq("a fresh window gets exactly the mark",peek(q),mark);
   eq("the flag is cached, not looked up again",trustedOverlayFlag(),mark);
  }else{
   eq("without the constant there is no mark to set",trustedOverlayFlag(),0);
   if(field){
    WindowManager.LayoutParams p=new WindowManager.LayoutParams();
    poke(p,owned);
    markTrustedOverlay(p);
    eq("a platform without the constant is left alone",peek(p),owned);
   }else{
    WindowManager.LayoutParams p=new WindowManager.LayoutParams();
    markTrustedOverlay(p);
    eq("a platform without either name is left alone",privateFlagsOf(p),0);
   }
  }

  if(field){
   WindowManager.LayoutParams p=new WindowManager.LayoutParams();
   poke(p,owned);
   eq("privateFlagsOf reports the word it was given",privateFlagsOf(p),owned);
  }else{
   WindowManager.LayoutParams p=new WindowManager.LayoutParams();
   eq("no field means no word to report, not a crash",privateFlagsOf(p),0);
   markTrustedOverlay(p);
   eq("no field means there is nothing to mark",privateFlagsOf(p),0);
  }

  System.out.println("PASS: "+checks+" cases over the trusted-overlay mark");
 }
}
"@
[System.IO.File]::WriteAllText("$src/OverlayTest.java", $test, (New-Object System.Text.UTF8Encoding($false)))

# The stub takes three shapes, because the platform may have both hidden
# names, only the field, or neither. Compiled separately: the shape is a
# compile-time fact.
$shapes=@(
  @{name='both';      field=$true;  flag=$true},
  @{name='noconstant';field=$true;  flag=$false},
  @{name='nofield';   field=$false; flag=$false}
)
$ran=0
foreach($shape in $shapes){
  $out="$classes/$($shape.name)"
  New-Item -ItemType Directory -Force $out | Out-Null
  $stub="package android.view;`npublic class WindowManager {`n public static class LayoutParams {`n"
  if($shape.field){$stub+="  public int privateFlags;`n"}
  if($shape.flag){$stub+="  public static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 1 << 29;`n"}
  $stub+=" }`n}`n"
  [System.IO.File]::WriteAllText("$src/WindowManager_$($shape.name).java", $stub, (New-Object System.Text.UTF8Encoding($false)))
  $treeSrc="$src/$($shape.name)"; New-Item -ItemType Directory -Force $treeSrc | Out-Null
  Copy-Item "$src/OverlayTest.java" $treeSrc
  New-Item -ItemType Directory -Force "$treeSrc/android/view" | Out-Null
  Copy-Item "$src/WindowManager_$($shape.name).java" "$treeSrc/android/view/WindowManager.java"
  & $javac -nowarn -d $out -encoding UTF-8 "$treeSrc/OverlayTest.java" "$treeSrc/android/view/WindowManager.java"
  if($LASTEXITCODE){throw "Compilation failed for the $($shape.name) tree"}
  & $java -Xmx128m -cp $out OverlayTest
  if($LASTEXITCODE){throw "Test failed for the $($shape.name) tree"}
  $ran++
}
Write-Output "PASS: $ran LayoutParams shapes"
