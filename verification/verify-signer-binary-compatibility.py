"""Compile against the old public data-class ABI, run unchanged bytecode against the new AAR."""
import argparse,json,os,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--current',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
jdk=Path(os.environ['JAVA_HOME'])/'bin'
stdlib=next((Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.10').glob('*/kotlin-stdlib-2.2.10.jar'))
for label,aar in [('old',args.baseline),('new',args.current)]:
 with zipfile.ZipFile(aar) as z:(args.output/f'{label}.jar').write_bytes(z.read('classes.jar'))
source=args.output/'LegacySignerClient.java'
source.write_text('''import java.util.*;
import jp.linkserver.goldy.*;
public final class LegacySignerClient {
 public static void main(String[] args) {
  ApkSigners s = new ApkSigners(Collections.singleton("a".repeat(64)), false);
  ApkSigners copy = s.copy(s.component1(), s.component2());
  ApkMetadata a = new ApkMetadata("app", "1.2.0-IntDev_rev10", 10L, copy);
  ApkMetadata b = a.copy(a.component1(),a.component2(),a.component3(),a.component4());
  if (!a.equals(b) || !s.equals(copy)) throw new AssertionError();
  System.out.println(a);System.out.println(b.getSigners().getCurrentSha256());
 }
}''',encoding='utf-8')
def cp(label):return os.pathsep.join(str(x) for x in [args.output,args.output/f'{label}.jar',stdlib])
subprocess.run([str(jdk/'javac.exe'),'--release','11','-encoding','UTF-8','-cp',cp('old'),str(source)],check=True,timeout=60)
outputs={label:subprocess.run([str(jdk/'java.exe'),'-cp',cp(label),'LegacySignerClient'],capture_output=True,text=True,check=True,timeout=30).stdout for label in ['old','new']}
assert outputs['old']==outputs['new']
result={'passed':True,'legacyConstructorCopyComponentsEqualityAndToStringRetained':True,'oldBytecodeRunsWithNewOnly':True}
(args.output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
