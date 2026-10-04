#!/usr/bin/env python3
"""Non-destructive smoke on a disposable CI emulator, using the canonical APK."""
import hashlib,json,pathlib,re,subprocess,time,xml.etree.ElementTree as ET,zlib,struct
OUT=pathlib.Path('ci-evidence'); SC=OUT/'screens';SC.mkdir(parents=True,exist_ok=True)
PKG='com.swipedelete.zero.debug';APK=OUT/'apk/app-play-debug.apk';steps=[]
def adb(*args): return subprocess.check_output(['adb',*args],stderr=subprocess.STDOUT)
def capture(name):
 adb('shell','uiautomator','dump','/sdcard/swiperise-window.xml')
 raw=adb('shell','cat','/sdcard/swiperise-window.xml');(SC/(name+'.xml')).write_bytes(raw)
 (SC/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 return ET.fromstring(raw)
def nodes(): return list(capture('latest').iter('node'))

def scroll_to(pattern, direction='down', max_scrolls=8):
 """Reach an actual lazy-list control without assuming it is initially composed."""
 for attempt in range(max_scrolls+1):
  tree=capture('latest')
  if any(re.search(pattern,(n.get('text','')+' '+n.get('content-desc','')).strip()) for n in tree.iter('node')):
   return tree
  if attempt == max_scrolls: break
  scrollables=[n for n in tree.iter('node') if n.get('scrollable')=='true']
  assert scrollables, 'No scroll surface while seeking: '+pattern
  x1,y1,x2,y2=map(int,re.findall(r'\d+',scrollables[-1].get('bounds')))
  top=y1+(y2-y1)//4;bottom=y2-(y2-y1)//4
  start,end=(bottom,top) if direction=='down' else (top,bottom)
  adb('shell','input','swipe',str((x1+x2)//2),str(start),str((x1+x2)//2),str(end),'450')
  time.sleep(1)
 raise AssertionError('Missing scroll-reachable control: '+pattern)
def click(pattern, timeout=20, scroll=False):
 deadline=time.time()+timeout
 scrolls=0
 while time.time()<deadline:
  tree=capture('latest')
  parents={child:parent for parent in tree.iter() for child in parent}
  for n in tree.iter('node'):
   ancestors=[n]
   while ancestors[-1] in parents: ancestors.append(parents[ancestors[-1]])
   if re.search(pattern,(n.get('text','')+' '+n.get('content-desc','')).strip()) and n.get('enabled')=='true' and not any(a.get('enabled')=='false' for a in ancestors):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1);return
  if scroll and scrolls < 3:
   width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size').decode())[-1])
   adb('shell','input','swipe',str(width//2),str(int(height*.78)),str(width//2),str(int(height*.32)),'450')
   scrolls+=1
  time.sleep(1)
 raise AssertionError('Missing enabled control: '+pattern)
def start(): adb('shell','am','start','-W','-n',PKG+'/com.swipedelete.zero.MainActivity');time.sleep(2)
def png(color):
 def chunk(t,b): return struct.pack('!I',len(b))+t+b+struct.pack('!I',zlib.crc32(t+b)&0xffffffff)
 data=b''.join(b'\0'+bytes(color)*512 for _ in range(512))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',512,512,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b'')
try:
 assert adb('shell','getprop','ro.kernel.qemu').strip()==b'1','Disposable emulator required'
 adb('install','-r',str(APK));steps.append('install canonical APK')
 for perm in ['READ_MEDIA_IMAGES','READ_MEDIA_VIDEO']:
  adb('shell','pm','grant',PKG,'android.permission.'+perm)
 test_apk=OUT/'apk/app-play-debug-androidTest.apk'
 adb('install','-r',str(test_apk))
 result=adb('shell','am','instrument','-w','-e','class','com.swipedelete.zero.MediaFixtureInstrumentedTest',PKG+'.test/androidx.test.runner.AndroidJUnitRunner').decode()
 (OUT/'media-fixtures.txt').write_text(result)
 assert 'OK (1 test)' in result,result
 media=adb('shell','content','query','--uri','content://media/external/images/media','--projection','_id:_display_name:_size:is_pending').decode()
 (OUT/'media-fixtures.txt').write_text(result+'\n'+media)
 for i in range(3):
  assert re.search(rf'Screenshot_fixture_{i}\.png.*_size=(?!NULL|0\b)\d+',media), 'Committed synthetic image missing from MediaStore'
 steps.append('three positive-size synthetic images committed via MediaStore')
 start();capture('01-dashboard');steps.append('permissions and dashboard')
 click(r'Start review|Browse library', scroll=True)
 if any('Got it' in n.get('text','') for n in nodes()):click(r'Got it')
 click(r'^Keep\b');click(r'^Undo\b');steps.append('keep and undo')
 click(r'^Stage\b');click(r'^Undo\b');click(r'^Stage\b');steps.append('stage, undo, stage again')
 capture('02-review');click(r'^Back\b')
 click(r'Review [1-9].*staged files');capture('03-staging')
 scroll_to(r'Cleanup is unavailable')
 assert any('Cleanup is unavailable' in n.get('text','') for n in nodes()), 'Safety lock explanation missing'
 # Both selection modes may change, but neither execution control can be enabled.
 for mode in ['Permanent Delete','30-Day OS Trash']:
  scroll_to(mode, direction='up')
  click(mode)
  scroll_to(r'Move to Android Trash|Delete and Free Up')
  tree=capture('04-lock-'+('permanent' if mode.startswith('Permanent') else 'trash'))
  parents={child:parent for parent in tree.iter() for child in parent}
  controls=[n for n in tree.iter('node') if ('Move to Android Trash' in n.get('text','') or 'Delete and Free Up' in n.get('text',''))]
  assert controls, 'Cleanup control not found'
  for control in controls:
   ancestry=[control]
   while ancestry[-1] in parents: ancestry.append(parents[ancestry[-1]])
   assert any(n.get('enabled')=='false' for n in ancestry), 'Cleanup control enabled'
 steps.append('locked cleanup explanation and modes')
 adb('shell','am','force-stop',PKG);start();click(r'Review [1-9].*staged files');capture('05-persisted')
 click(r'Unstage all');steps.append('queue survives restart; unstage works')
 capture('06-restored')
 for i in range(3):
  assert re.search(rf'Screenshot_fixture_{i}\.png.*_size=(?!NULL|0\b)\d+',adb('shell','content','query','--uri','content://media/external/images/media','--projection','_display_name:_size').decode())
 steps.append('all synthetic originals remain')
 status='PASS'
except Exception as e:
 status='FAILED';steps.append(str(e));(OUT/'failure-logcat.txt').write_bytes(adb('logcat','-d','-t','1200'));raise
finally:
 (OUT/'smoke.json').write_text(json.dumps({'status':status,'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),'steps':steps,'scope':'isolated emulator, synthetic local files; no cloud or deletion'},indent=2)+'\n')
