import tempfile,unittest
from pathlib import Path
from stop_application import find_pids
class StopTest(unittest.TestCase):
 def test_only_exact_installation_jar(self):
  with tempfile.TemporaryDirectory() as f:
   base=Path(f);root=base/'app';root.mkdir();proc=base/'proc';proc.mkdir()
   for pid,cmd,cwd in [(1,['java','-jar','build/libs/base-repo.jar'],root),(2,['java','-jar','build/libs/base-repo.jar'],base),(3,['grep','base-repo.jar'],root)]:
    p=proc/str(pid);p.mkdir();(p/'cmdline').write_bytes(b'\0'.join(x.encode() for x in cmd)+b'\0');(p/'cwd').symlink_to(cwd,target_is_directory=True)
   self.assertEqual(find_pids(root,proc),[1])
