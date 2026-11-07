"""Create tiny, real signed APKs with disposable keys. Never reads a release signing key.

Private test keys stay under ignored build/rotation-fixtures/keys. Only public APK fixtures
are copied into test resources. Run with JDK21 and Android SDK build-tools 36.0.0.
"""
import argparse, json, os, subprocess, zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--android-home',type=Path,required=True)
args=p.parse_args()
root=Path(__file__).resolve().parent.parent
work=root/'build/rotation-fixtures';work.mkdir(parents=True,exist_ok=True)
keys=work/'keys';keys.mkdir(exist_ok=True)
java=args.java_home/'bin/java.exe';keytool=args.java_home/'bin/keytool.exe'
tools=args.android_home/'build-tools/36.0.0'
signer=[java,'-jar',tools/'lib/apksigner.jar']
password='goldy-disposable-fixture'
def run(*cmd):
    r=subprocess.run([str(x) for x in cmd],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=90)
    if r.returncode: raise RuntimeError(r.stdout+r.stderr)
    return r.stdout
def key(k):return ['--ks',keys/f'{k}.p12','--ks-key-alias',k,'--ks-pass',f'pass:{password}']
for k in ['a','b','c','x']:
    if not (keys/f'{k}.p12').exists():
        run(keytool,'-genkeypair','-keystore',keys/f'{k}.p12','-storetype','PKCS12','-storepass',password,
            '-keypass',password,'-alias',k,'-keyalg','RSA','-keysize','2048','-validity','3650','-dname',f'CN=Goldy disposable fixture {k}')
def lineage(name,old,new,parent=None,cap=True):
    cmd=[*signer,'rotate','--out',work/f'{name}.lineage']
    if parent:cmd+=['--in',work/f'{parent}.lineage']
    run(*cmd,'--old-signer',*key(old),'--set-installed-data',str(cap).lower(),'--set-rollback','false','--new-signer',*key(new))
lineage('ab','a','b');lineage('abc','b','c','ab');lineage('ab-denied','a','b',cap=False)
manifest=work/'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="jp.linkserver.goldy.rotationfixture" android:versionCode="10" android:versionName="1.2.0-IntDev_rev10"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application android:hasCode="false" android:label="Goldy signature fixture"/></manifest>''',encoding='utf-8')
unsigned=work/'unsigned.apk'
run(tools/'aapt2.exe','link','-o',unsigned,'--manifest',manifest,'-I',args.android_home/'platforms/android-37.0/android.jar')
out=root/'gold/src/test/resources/signatures';out.mkdir(parents=True,exist_ok=True)
def sign(name,signers,line=None,rotation=None,multi=False):
    cmd=[*signer,'sign','--in',unsigned,'--out',out/f'{name}.apk','--min-sdk-version','28',
         '--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled',str(not multi).lower(),
         '--v4-signing-enabled','false']
    if line:cmd+=['--lineage',work/f'{line}.lineage','--rotation-min-sdk-version',str(rotation)]
    for i,k in enumerate(signers):
        if i:cmd+=['--next-signer']
        cmd+=key(k)
    run(*cmd)
sign('a',['a']);sign('b-unrelated',['b']);sign('x',['x'])
sign('ab-v3',['a','b'],'ab',28);sign('abc-v3',['a','c'],'abc',28)
sign('ab-denied',['a','b'],'ab-denied',28)
sign('ab-v31',['a','b'],'ab',33)
sign('multi-ab',['a','b'],multi=True);sign('multi-ac',['a','c'],multi=True)
# Mutate authenticated APK contents without modifying ZIP lengths, retaining the signature block.
data=bytearray((out/'ab-v3.apk').read_bytes())
with zipfile.ZipFile(out/'ab-v3.apk') as z:
    entry=z.getinfo('AndroidManifest.xml');pos=entry.header_offset
    name_len=int.from_bytes(data[pos+26:pos+28],'little');extra_len=int.from_bytes(data[pos+28:pos+30],'little')
    payload=pos+30+name_len+extra_len
data[payload+max(1,entry.compress_size//2)]^=1
(out/'tampered.apk').write_bytes(data)
# Corrupt a PoR signature/structure while retaining the APK's unsigned test manifest.
data=bytearray((out/'ab-v3.apk').read_bytes())
marker=bytes.fromhex('8c6fa03b') # V3SchemeConstants.PROOF_OF_ROTATION_ATTR_ID, little endian.
pos=data.find(marker)
if pos<0:raise RuntimeError('PoR attribute missing')
data[pos+16]^=1
(out/'tampered-lineage.apk').write_bytes(data)
print(json.dumps({'publicFixtures':[f.name for f in out.glob('*.apk')],'privateKeysCommitted':False},indent=2))
