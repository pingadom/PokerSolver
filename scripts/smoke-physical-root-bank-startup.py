"""Owned startup provenance plus the complete unchanged three-hand HTTP contract."""
import argparse
import hashlib
import importlib.util
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/smoke-root-hand-bank.py'
HELPER_SHA='b3a1a2fb6e86028d66255032fddcfc9f4b4a65d48221e91d6c16bbb005dd215c'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!=HELPER_SHA:raise ValueError('Original HTTP smoke helper changed')
spec=importlib.util.spec_from_file_location('physical_root_startup_http_contract',HELPER);legacy=importlib.util.module_from_spec(spec);spec.loader.exec_module(legacy)
PHYSICAL_PIN='feb331638b447a30be52d8fd9bfe6c267120afe29ba0b51c94b416f7d068771e'

def expected(source):
    if source not in ['ORIGINAL','PHYSICAL_PARENT_REUSE']:raise ValueError('Explicit startup source required')
    reused=source=='PHYSICAL_PARENT_REUSE'
    return dict(schemaVersion='pokerlab-root-hand-bank-startup/v1',startupSource=source,studyHash=legacy.PIN,
        sourceProofHash=PHYSICAL_PIN if reused else legacy.PIN,trainerAdmission=False,
        startupBudget=dict(physicalGames=1,maximumParentSolveCalls=1 if reused else 4,maximumParentLpSolves=2 if reused else 8,maximumEnvelopeLpSolves=15,freshReferenceRuns=2,freshIterations=240000),
        availableQuestions=3,cachedLegalGrades=9,publicMetadataMeaning='UNCHANGED_HISTORICAL_PUBLIC_PROTOCOL_RESERVATION')

def run(source='PHYSICAL_PARENT_REUSE',disabled=False):
    target=expected(source)
    legacy.run(disabled)
    if disabled:
        legacy.call('/startup',None,404);return
    actual=legacy.call('/startup')
    # Require typed numbers/booleans, not Python's True==1 or 2.0==2 coercion.
    def exact(a,b):
        if type(a) is not type(b):raise AssertionError('Startup field type differs')
        if isinstance(b,dict):
            assert a.keys()==b.keys()
            for k,v in b.items():exact(a[k],v)
        else:assert a==b
    exact(actual,target)
    assert legacy.call('/startup')==actual
    print('Owned startup provenance, unchanged public metadata,3 questions,9 grades and canonical reviews passed: '+source)
    return actual

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',choices=['ORIGINAL','PHYSICAL_PARENT_REUSE'],default='PHYSICAL_PARENT_REUSE');p.add_argument('--disabled',action='store_true');p.add_argument('--base-url',default='http://localhost:18080');a=p.parse_args();legacy.BASE=a.base_url.rstrip('/')+legacy.PREFIX;run(a.source,a.disabled)
