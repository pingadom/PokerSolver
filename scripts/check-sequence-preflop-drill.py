"""Independent public projection, all cached grades and canonical review; no optimizer/training."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sequence_cache_source',ROOT/'scripts/check-root-sequence-feedback.py')
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require,read=feedback.require,feedback.read
IMMUTABLE_HELPERS={'check-root-sequence-feedback.py': '473ce94f287446c8cc9faff0f0c7687ca6152e9c13cb060ee8068f4c29a18f3c', 'check-physical-root-sequence-minimum.py': '5d8ea982b786bb22256ca5d85e0488394733457de978487ae170c7fb1f13cec3', 'check-root-minimum-feedback.py': '683e1f614d333d4a881ed4223baae2f1d149115a5414f73b56dc553bbb071c69', 'check-root-minimum.py': '12f49d03dd35e19cdb56fa498b83014787cae75bd70042d271b2933744cc9c7d', 'check-root-sequence-minimax.py': '5a242aa3b791e04807e5c3f0426633db2c03c5001554f24461f2198db97ce765', 'check-root-envelope-feedback.py': 'e0e7d3e803fe6d2de99b893ccda9e0e3ce71b93b74580be398c3c6ac03fdcf48', 'check-root-security-envelope.py': 'dd871cc2d9394a52263a2f8945d5713e2f9e0a8a21fde0be0ead0bc60c28a0b1', 'check-reduced-preflop.py': '4202b10cbeddab54d20708b87d5769403bbbf4dda3a604df3518299a851d0960', 'check-root-minimax.py': 'a41d0959f74c5e00a0a43843c13b31107d396fdd8da43b0f87e4685a84ad6cc5', 'check-resolvable-refined-lp.py': '84795dabca56dd86a4870b23efd8d5731d4b4b17d3399fbfa818f7ee9d18f080'}
for name,value in IMMUTABLE_HELPERS.items():
    require(hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest()==value,'Immutable projection helper changed: '+name)
DATA=ROOT/'docs/data'
PIN='586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-sequence-preflop-drill-manifest.json'
FIXTURE=DATA/'supplied-sequence-preflop-drill-fixture.json'
REVIEW=DATA/'supplied-sequence-preflop-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=2,maximumParentLpSolves=4,maximumMinimumLpSolves=1,freshReferenceRuns=2,freshIterations=240000)


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical(value,level=0):
    """Jackson's existing sorted default-pretty layout for this frozen review protocol."""
    if isinstance(value,dict):
        if not value:return '{ }'
        return '{\n'+',\n'.join('  '*(level+1)+json.dumps(k)+' : '+canonical(v,level+1) for k,v in sorted(value.items()))+'\n'+'  '*level+'}'
    if isinstance(value,list):return '[ '+', '.join(canonical(v,level) for v in value)+' ]' if value else '[ ]'
    if isinstance(value,float):
        return re.sub(r'e([+-]?)(0*)(\d+)',lambda m:'E'+('-' if m[1]=='-' else '')+m[3],repr(value))
    return json.dumps(value,ensure_ascii=False,separators=(',',':'),allow_nan=False)


def review(choices,grades):
    require(len(choices)<=128,'Attempt cap exceeded')
    attempts=[];counts=dict(distinctQuestions=1 if choices else 0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    fields=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for choice in choices:
        require(set(choice)=={'sourceCaseIndex','action'} and type(choice['sourceCaseIndex']) is int and choice['sourceCaseIndex']==0,'Original choice identity required')
        matches=[g for g in grades if g['move']['action']==choice['action']]
        require(len(matches)==1,'Unknown cached choice')
        grade=matches[0];attempts.append(dict(ordinal=len(attempts),attempt=choice,grade=grade));counts[fields[grade['move']['classification']]]+=1
    return dict(studyHash=PIN,attempts=attempts,counts=counts)


def source_context():
    source=read(feedback.REPORT)
    return source,feedback.check_values(read(feedback.REQUEST),source)


def check_values(manifest,fixture,export,verified=None):
    expected={'schemaVersion': 'pokerlab-sequence-preflop-drill-manifest/v1', 'studyHash': '586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21', 'startupBudget': {'physicalGames': 1, 'maximumParentSolveCalls': 2, 'maximumParentLpSolves': 4, 'maximumMinimumLpSolves': 1, 'freshReferenceRuns': 2, 'freshIterations': 240000}, 'requestFile': 'supplied-root-sequence-feedback-request.json', 'requestSha256': '77861632b5f1260071b89d7c465e812fff74547beef35fa776fb4562b7f13c18', 'reportFile': 'supplied-root-sequence-feedback-report.json.gz', 'reportStoredSha256': '013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b', 'envelopeFile': 'supplied-root-envelope-feedback-report.json.gz', 'envelopeStoredSha256': 'e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d', 'minimumFile': 'supplied-root-sequence-minimum-report.json.gz', 'minimumStoredSha256': '1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f', 'legacyMinimumFile': 'supplied-root-minimum-report.json.gz', 'legacyMinimumStoredSha256': 'ea48889cd6677102c917d1990e6b14dc576a12dad75bc7a86d3fb21cc1906403'}
    feedback.exact(manifest,expected,'Published sequence source manifest differs')
    for name,field in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),
            ('envelopeFile','envelopeStoredSha256'),('minimumFile','minimumStoredSha256'),
            ('legacyMinimumFile','legacyMinimumStoredSha256')]:
        require(sha(DATA/manifest[name])==manifest[field],'Approved stored source bytes differ')
    source,source_receipt=verified if verified is not None else source_context()
    require(source_receipt['qualifiedForOfflineDrill'] and source['rejectionReasons']==[],'Qualified source required')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-sequence-preflop-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],trainerAdmission=False,
                  model=source['sourceMinimum']['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,availableQuestions=1,cachedLegalGrades=3,maximumAttempts=128,startupBudget=BUDGET)
    feedback.exact(fixture['metadata'],metadata,'Public metadata or EV scope differs')
    context=source['sourceMinimum']['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
    require(commit==1 and context['actingSeat']=='BB','Original acting commitment differs')
    moves=copy.deepcopy(source['moves'])
    for move in moves:move['lowerEvBb']+=commit;move['upperEvBb']+=commit
    question=dict(sourceCaseIndex=0,heroCards='9d 9h',board=[],context=context,evScope=SCOPE,moves=moves)
    feedback.exact(fixture['questions'],[question],'Public preflop projection differs or hidden information leaked')
    grades=[dict(studyHash=PIN,sourceCaseIndex=0,evScope=SCOPE,move=m) for m in moves]
    feedback.exact(fixture['grades'],grades,'Cached grade, shifted EV, loss or ambiguity differs')
    choices=[dict(sourceCaseIndex=0,action=m['action']) for m in moves]
    expected=review(choices,grades);feedback.exact(fixture['review'],expected,'Canonical review values/order/counts differ')
    require(export==canonical(expected)+'\n' and '\r' not in export,'Canonical LF export bytes differ')
    require([g['move']['classification'] for g in grades]==['OUTSIDE_LIMIT','WITHIN_LIMIT','STRATEGY_DEPENDENT'],'Raise ambiguity lost')
    return dict(schemaVersion='pokerlab-sequence-preflop-drill-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
                canonicalJavaSourcePinVerifiedByOwnedReplay=PIN,publicQuestionsChecked=1,cachedGradesChecked=3,evScope=SCOPE,
                emptyPreflopBoard=True,hiddenDealsAndFutureBoardAbsent=True,canonicalReviewAttemptsChecked=3,sourceCommitmentShiftBb=commit,freshLpSolves=0,freshCfrRuns=0)


def check():
    result=check_values(read(MANIFEST),read(FIXTURE),REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),sourceStoredSha256={p.name:sha(p) for p in [MANIFEST,FIXTURE,REVIEW]},
                  immutableHelperSha256=IMMUTABLE_HELPERS)
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(data)
    print(data.decode())
