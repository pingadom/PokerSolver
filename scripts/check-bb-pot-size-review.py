"""Strict 1-MiB review reader and independent regrade of the four canonical pot-size attempts."""
import argparse
import gzip
import hashlib
import importlib.util
import io
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'docs/data';LIMIT=1024*1024
spec=importlib.util.spec_from_file_location('pot_review_source',ROOT/'scripts/check-bb-pot-size-feedback.py')
source=importlib.util.module_from_spec(spec);spec.loader.exec_module(source)
require=source.require
REVIEW=DATA/'supplied-unpaired-bb-pot-size-review.json'
BUDGET=DATA/'bb-pot-size-review-generation-budget.json'

def read_review(path):
    with Path(path).open('rb') as stream:raw=stream.read(LIMIT+1)
    require(len(raw)<=LIMIT,'Stored review byte cap exceeded')
    if str(path).endswith('.gz'):
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:expanded=stream.read(LIMIT+1)
    else:expanded=raw
    require(len(expanded)<=LIMIT,'Expanded review byte cap exceeded')
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,'Duplicate review JSON key');result[key]=value
        return result
    def number(text):
        value=float(text);require(math.isfinite(value),'Nonfinite review number');return value
    value=json.loads(expanded,object_pairs_hook=pairs,parse_float=number,
        parse_constant=lambda _:require(False,'Nonfinite review number'))
    return value,raw

def check(review_path=REVIEW,*,verify_source=True):
    receipt=source.check_source() if verify_source else None
    budget=source.read(BUDGET)[0]
    require(budget['requestSha256']==source.digest(source.REQUEST)
        and budget['sourceReportSha256']==source.digest(source.REPORT)
        and budget['sourceReceiptSha256']==source.digest(DATA/'bb-pot-size-feedback-independent-check.json')
        and budget['physicalGameBuilds']==1 and budget['maximumParentSolves']==18 and budget['maximumActionComputations']==12
        and budget['freshReferenceRuns']==2 and budget['totalReferenceIterations']==240000
        and budget['selectedPolicyAssessments']==18 and budget['cachedReviewGrades']==4
        and budget['automaticRetries']==0 and budget['maximumReviewBytes']==LIMIT,'Review generation budget differs')
    review,raw=read_review(review_path)
    result=source.check_review(review,source.read(source.REPORT)[0],source.read(source.PINS)[0])
    require([(a['feedback']['question']['index'],a['feedback']['action']) for a in review['attempts']]==[(0,'fold'),(0,'call'),(1,'fold'),(1,'call')],'Canonical question/action inventory differs')
    return dict(schemaVersion='pokerlab-independent-bb-pot-size-review/v1',trainerAdmission=False,freshLpSolves=0,freshCfrRuns=0,
        sourceCheck=receipt,reviewCheck=result,reviewStoredSha256=hashlib.sha256(raw).hexdigest(),
        checkerSourceSha256=source.digest(Path(__file__)),generationBudgetSha256=source.digest(BUDGET))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
    require(not args.output.exists(),'Receipt must be new');result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps(result,indent=2))
