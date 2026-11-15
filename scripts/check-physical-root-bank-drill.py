"""Verify the separate two-parent startup proof and unchanged three-hand public cache. No LP/CFR."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'docs/data'
HELPERS={'check-physical-root-bank-reuse.py': '6cc0de717cad3927442f2be467281e73b48776ae0522ae5814382eb908553171', 'check-root-hand-bank-drill.py': 'f94d71eb7dd693e55b4a697805efcb6548824bb1792c0de9cf4a6f4b3b73d7b4'}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def module(name,file):
    path=ROOT/'scripts'/file
    if sha(path)!=HELPERS[file]:raise ValueError('Immutable helper changed: '+file)
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
physical=module('physical_cache_owned_source','check-physical-root-bank-reuse.py')
public=module('physical_cache_public_protocol','check-root-hand-bank-drill.py')
require,exact,read=physical.require,physical.exact,physical.read
PIN=public.PIN
PROOF='feb331638b447a30be52d8fd9bfe6c267120afe29ba0b51c94b416f7d068771e'
MANIFEST=DATA/'supplied-physical-root-bank-drill-manifest.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=1,maximumParentLpSolves=2,maximumEnvelopeLpSolves=15,freshReferenceRuns=2,freshIterations=240000)
EXPECTED=dict(schemaVersion='pokerlab-physical-root-bank-drill-manifest/v1',studyHash=PIN,physicalStudyHash=PROOF,
 requestFile='supplied-physical-root-bank-reuse-request.json',requestSha256='36119e55e1b9eef745b1b7fcad2a2401b5ade450bf84bbdabdd9298155ea2975',
 reportFile='supplied-physical-root-bank-reuse-report.json.gz',reportStoredSha256='bb65da5c69e8689bdd758a6f80ece2b9c2fbbaedb903782d1be046ebef24ee5d',
 bankFile='supplied-root-hand-bank-report.json.gz',bankStoredSha256=physical.SOURCE_SHA,startupBudget=BUDGET)

def check_values(manifest,report,fixture,export):
    physical.bank.finite(manifest);exact(manifest,EXPECTED)
    physical_receipt=physical.check_values(physical.EXPECTED_REQUEST,report)
    source,receipt,_=physical.source()
    public_receipt=public.check_values(read(public.MANIFEST,16*1024),fixture,export,(source,receipt))
    require(physical_receipt['actualParentLpSolves']==2 and public_receipt['cachedGradesChecked']==9,'Complete physical and public evidence required')
    return dict(schemaVersion='pokerlab-physical-root-bank-drill-independent-check/v1',trainerAdmission=False,gradingAuthority=False,
       publicStudyHash=PIN,physicalProofHash=PROOF,startupBudget=BUDGET,publicMetadataReservationIsHistorical=True,
       physicalCheck=physical_receipt,publicCheck=public_receipt,freshLpSolves=0,freshCfrRuns=0)

def check():
    for file,pin in {**HELPERS,**public.IMMUTABLE_HELPERS}.items():require(sha(ROOT/'scripts'/file)==pin,'Immutable helper changed: '+file)
    manifest=read(MANIFEST,16*1024)
    exact(manifest,EXPECTED)
    for file,field in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),('bankFile','bankStoredSha256')]:require(sha(DATA/manifest[file])==manifest[field],'Pinned captured bytes differ')
    import gzip
    raw=gzip.decompress((DATA/manifest['reportFile']).read_bytes()).decode('utf-8')
    require(hashlib.sha256(physical.compact(raw)).hexdigest()==PROOF,'Independent lexical physical identity differs')
    result=check_values(manifest,read(DATA/manifest['reportFile']),read(public.FIXTURE),public.REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),manifestStoredSha256=sha(MANIFEST),immutableHelperSha256=HELPERS)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--receipt',type=Path);a=p.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if a.receipt:
        with a.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
