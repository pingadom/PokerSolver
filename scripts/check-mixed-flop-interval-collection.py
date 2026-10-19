"""Independently verify six fixed BB/BTN decisions and all three original source proofs."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys
import importlib.util

ROOT = Path(__file__).resolve().parents[1]

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = result
    spec.loader.exec_module(result)
    return result

old = module('mixed_flop_old', 'check-flop-interval-collection.py')
btn = module('mixed_flop_btn', 'check-btn-flop-drill.py')
require, exact = btn.require, btn.exact
SCHEMA = 'pokerlab-flop-interval-collection-review/v2'
SELECTION = 'ORDERED_TWO_BB_AND_FOUR_BTN_FLOP_QUESTIONS/v2'
KINDS = ['INITIAL_CHECK_BET', 'BB_FACING_BET', 'BTN_AFTER_CHECK']
PINS = old.PINS + [btn.source.REPORT_HASH]
SOURCES = [dict(kind=k, requestFile=f'supplied-four-button-hand-{suffix}-request.json',
                reportFile=f'supplied-four-button-hand-{suffix}-report.json.gz', reportHash=pin)
           for k,suffix,pin in zip(KINDS,['flop-feedback','later-bb-flop-feedback','btn-curriculum'],PINS,strict=True)]
INVENTORY = [dict(kind=KINDS[i], reportHash=PINS[i], sourceQuestionIndex=0) for i in range(2)] + [
    dict(kind=KINDS[2], reportHash=PINS[2], sourceQuestionIndex=i) for i in range(4)]
IDENTITY = dict(schemaVersion=SCHEMA, selection=SELECTION, questions=INVENTORY)
PIN = hashlib.sha256(json.dumps(IDENTITY,sort_keys=True,separators=(',',':')).encode()).hexdigest()
DRILLS = old.DRILLS + [ROOT / 'docs/data/supplied-four-button-hand-btn-flop-drill.json']


def read(path, limit):
    with path.open('rb') as stream:
        raw = stream.read(limit + 1)
    require(len(raw) <= limit, 'Stored mixed artifact exceeds cap')
    if path.suffix == '.gz':
        with gzip.open(path,'rb') as stream:
            expanded = stream.read(limit + 1)
    else:
        expanded = raw
    require(len(expanded) <= limit, 'Expanded mixed artifact exceeds cap')
    def unique(pairs):
        result = {}
        for key,value in pairs:
            require(key not in result, 'Duplicate mixed JSON key')
            result[key] = value
        return result
    def invalid(value):
        raise ValueError('Nonfinite mixed JSON number: ' + value)
    return raw, expanded, json.loads(expanded,object_pairs_hook=unique,parse_constant=invalid)


def decision(kind, value):
    return dict(kind=kind,initial=value if kind==KINDS[0] else None,
                later=value if kind==KINDS[1] else None,btn=value if kind==KINDS[2] else None)


def verify_review(review, drills):
    values = [drills[0]['question'],drills[1]['question'],*drills[2]['questions']]
    questions = [dict(collectionHash=PIN,index=i,totalQuestions=6,selection=SELECTION,
                      sourceQuestionIndex=local['sourceQuestionIndex'],decision=decision(local['kind'],q))
                 for i,(local,q) in enumerate(zip(INVENTORY,values,strict=True))]
    require(isinstance(review,dict) and set(review)=={'schemaVersion','collectionHash','selection','questions','attempts','counts'}
            and review['schemaVersion']==SCHEMA and review['collectionHash']==PIN and review['selection']==SELECTION
            and exact(review['questions'],questions), 'Exact six-question typed inventory differs')
    attempts = review['attempts']
    require(isinstance(attempts,list) and len(attempts)<=100, 'Mixed review attempt cap differs')
    counts = dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    names = dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    seen = set()
    for index,a in enumerate(attempts):
        require(isinstance(a,dict) and set(a)=={'index','questionIndex','action','feedback'}
                and type(a['index']) is int and a['index']==index and type(a['questionIndex']) is int
                and 0<=a['questionIndex']<6, 'Mixed attempt identity or order differs')
        qi = a['questionIndex']; local = INVENTORY[qi]; source_index = min(qi,2)
        matches = [x['feedback'] for x in drills[source_index]['attempts'] if x['action']==a['action']
                   and (qi<2 or x['questionIndex']==local['sourceQuestionIndex'])]
        require(len(matches)==1, 'Illegal action or source-local question index')
        grade = matches[0]
        feedback = dict(collectionHash=PIN,questionIndex=qi,sourceQuestionIndex=local['sourceQuestionIndex'],
                        decision=decision(local['kind'],grade))
        require(exact(a['feedback'],feedback), 'Exact typed mixed feedback differs')
        seen.add(qi); counts[names[grade['selected']['classification']]] += 1
    counts['distinctQuestionsAttempted'] = len(seen)
    require(exact(review['counts'],counts), 'Mixed distinct-context or attempt counts differ')
    return len(attempts)


def check(manifest_path, review_path):
    manifest_raw, _, manifest = read(manifest_path,64*1024)
    require(exact(manifest,dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v2',collectionHash=PIN,sources=SOURCES)),
            'Fixed mixed manifest identity/order differs')
    root = manifest_path.resolve().parent
    a,b,c = SOURCES
    receipts = [old.initial.check(root/a['requestFile'],root/a['reportFile'],DRILLS[0]),
                old.later.check(root/b['requestFile'],root/b['reportFile'],DRILLS[1]),
                btn.check(root/c['requestFile'],root/c['reportFile'],DRILLS[2])]
    raw, expanded, review = read(review_path,1024*1024)
    drills = [read(p,1024*1024)[2] for p in DRILLS]
    attempts = verify_review(review,drills)
    return dict(schemaVersion='pokerlab-independent-mixed-flop-interval-collection/v2',trainerAdmission=False,
                fixedCaseOnly=True,freshLpSolves=0,freshCfrRuns=0,collectionHash=PIN,sourceQuestionsChecked=6,
                originalEndpointCertificatesChecked=sum(r['originalEndpointCertificatesChecked'] for r in receipts),
                sourcePolicyContextChecks=18,attemptsChecked=attempts,sourceReceipts=receipts,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                manifestStoredBytesSha256=hashlib.sha256(manifest_raw).hexdigest(),
                reviewStoredBytesSha256=hashlib.sha256(raw).hexdigest(),
                reviewExpandedBytesSha256=hashlib.sha256(expanded).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest',type=Path); parser.add_argument('review',type=Path)
    parser.add_argument('--output',type=Path); args = parser.parse_args()
    if args.output:
        protected = {args.manifest.resolve(),args.review.resolve(),*DRILLS}
        root = args.manifest.resolve().parent
        protected.update((root / s[key]).resolve() for s in SOURCES for key in ('requestFile','reportFile'))
        require(args.output.resolve() not in protected,'Receipt must be distinct from all inputs')
        require(not any(args.output.exists() and args.output.samefile(p) for p in protected),'Receipt aliases an input')
    text = json.dumps(check(args.manifest,args.review),indent=2)+'\n'
    if args.output:
        with args.output.open('x',encoding='utf-8',newline='\n') as stream:stream.write(text)
    print(text,end='')

if __name__=='__main__':main()
