#!/usr/bin/env python3
"""Generate canonical evidence from current CI outputs; never reuse tracked evidence."""
import hashlib,json,os,pathlib,subprocess,sys,zipfile,datetime,re
ROOT=pathlib.Path('ci-evidence');ROOT.mkdir(exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
def write(name,obj): (ROOT/name).write_text(json.dumps(obj,indent=2)+'\n')
if sys.argv[1]=='identity':
 event=json.loads(pathlib.Path(os.environ['GITHUB_EVENT_PATH']).read_text());pr=event.get('pull_request',{})
 write('checkout.json',dict(checkout_sha=git('rev-parse','HEAD'),tree_sha=git('rev-parse','HEAD^{tree}'),parents=git('show','-s','--format=%P','HEAD'),pr_head=pr.get('head',{}).get('sha'),base_sha=pr.get('base',{}).get('sha'),run_id=os.environ['GITHUB_RUN_ID'],run_attempt=os.environ['GITHUB_RUN_ATTEMPT'],started_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),clean=not bool(git('status','--porcelain'))))
else:
 apk=ROOT/'apk/app-play-debug.apk';audit=json.loads((ROOT/'junit_audit.json').read_text());smoke=json.loads((ROOT/'smoke.json').read_text())
 assert audit['status']=='XML_COUNTS_VALID' and smoke['status']=='PASS'
 assert smoke['apk_sha256']==sha(apk)
 manifest=(ROOT/'logs/apk-badging.log').read_text();cert=(ROOT/'logs/apksigner.log').read_text()
 package=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",manifest)
 assert package and package[1]=='com.swipedelete.zero.debug'
 fingerprint=re.search(r'Signer #1 certificate SHA-256 digest: (\w+)',cert)
 assert fingerprint,'Verified signing fingerprint missing'
 identity=dict(checkout=json.loads((ROOT/'checkout.json').read_text()),apk=dict(path='apk/app-play-debug.apk',sha256=sha(apk),size_bytes=apk.stat().st_size,package=package[1],version_code=package[2],version_name=package[3],min_sdk=re.search(r"sdkVersion:'(\d+)'",manifest)[1],target_sdk=re.search(r"targetSdkVersion:'(\d+)'",manifest)[1],signer_sha256=fingerprint[1],signing='debug; not store release'),tests=audit['totals'],by_variant=audit['by_variant'],smoke=smoke,status='READY_FOR_REVIEW',limitations=['No physical device test','No live Google OAuth/upload/restore test','Play/cloud deletion remains locked','Not production/store approval'])
 write('build_identity.json',identity);write('handoff.json',identity)
 skips=[f"- {c['variant']}: {c['classname']}.{c['name']} — {c['skip_reason']}; passes on {c['covered_by_variant']}" for c in audit['cases'] if c['outcome']=='skipped']
 (ROOT/'HANDOFF.md').write_text('# SwipeRise test build — ready for review\n\n'+f"Checkout: `{identity['checkout']['checkout_sha']}`\n\nAPK SHA-256: `{sha(apk)}`\n\n"+json.dumps(audit['by_variant'],indent=2)+'\n\n'+'\n'.join(skips)+'\n\n'+ '\n'.join('- '+x for x in identity['limitations'])+'\n')
 paths=sorted(p for p in ROOT.rglob('*') if p.is_file() and p.name!='SHA256SUMS.txt')
 (ROOT/'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.relative_to(ROOT).as_posix()}\n' for p in paths))
 with zipfile.ZipFile('swiperise-evidence.zip','w',zipfile.ZIP_DEFLATED) as z:
  for p in sorted(ROOT.rglob('*')):
   if p.is_file():z.write(p,p.relative_to(ROOT))
 pathlib.Path('swiperise-evidence.zip.sha256').write_text(sha(pathlib.Path('swiperise-evidence.zip'))+'  swiperise-evidence.zip\n')
 with zipfile.ZipFile('swiperise-evidence.zip') as z:
  for line in z.read('SHA256SUMS.txt').decode().splitlines():
   digest,name=line.split(maxsplit=1);assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
 print('Canonical archive verified:',sha(pathlib.Path('swiperise-evidence.zip')))
