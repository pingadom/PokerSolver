"""Independently reconstruct a qualified refined flop question, interval grades and saved review."""
import argparse
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-refined-flop-feedback.py'
spec=importlib.util.spec_from_file_location('refined_drill_feedback',HELPER)
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require=feedback.require
PIN='1a5254e971afcc76a3e5b1eba7c1c61f90afd8de17684842846c63da0afe6173'
STATUS='REFINED_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY'
SCHEMA='pokerlab-refined-flop-drill/v1'
SELECTION='SINGLE_QUALIFIED_REFINED_FLOP_QUESTION/v1'
LIMIT=256*1024


def read(path):
    with path.open('rb') as stream:stored=stream.read(LIMIT+1)
    require(len(stored)<=LIMIT,'Stored drill cap exceeded')
    if path.suffix=='.gz':
        with gzip.GzipFile(fileobj=io.BytesIO(stored)) as stream:expanded=stream.read(LIMIT+1)
    else:expanded=stored
    require(len(expanded)<=LIMIT,'Expanded drill cap exceeded')
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,'Duplicate drill field');result[key]=value
        return result
    value=json.loads(expanded,object_pairs_hook=pairs,parse_constant=lambda _:require(False,'Nonfinite drill constant'))
    return value,stored


def check(request_path,report_path,drill_path):
    proof=feedback.check(request_path,report_path)
    report,_,_=feedback.read(report_path)
    require(proof['qualifiedForOfflineDrill'] is True,'Unqualified source cannot authorize a drill')
    source=report['conditional'];q=source['request']
    question=dict(reportHash=PIN,publicationStatus=STATUS,trainerAdmission=False,selection=SELECTION,informationSet=source['informationSet'],hero='BTN',heroCards=q['heroCards'],context=source['context'],model=source['parent']['binding']['model'],chanceModel=source['parent']['binding']['chanceModel'],beliefs=source['parent']['binding']['sourceBinding']['beliefs'],sourceHistoryReachStatus=source['parent']['binding']['sourceHistoryReachStatus'],maximumLossBb=report['request']['maximumLossBb'],securitySlackBb=1e-8,minimumReach=.0001,reachUnits=source['reachUnits'],evScope=source['evScope'])
    saved,raw=read(drill_path)
    require(set(saved)=={'schemaVersion','question','attempts','counts'} and saved['schemaVersion']==SCHEMA and saved['question']==question,'Drill question/provenance differs')
    require(isinstance(saved['attempts'],list) and len(saved['attempts'])<=100,'Attempt cap differs')
    moves={m['action']:m for m in report['moves']};attempts=[];counts=dict(withinLimit=0,outsideLimit=0,strategyDependent=0)
    count_fields={'WITHIN_LIMIT':'withinLimit','OUTSIDE_LIMIT':'outsideLimit','STRATEGY_DEPENDENT':'strategyDependent'}
    for i,attempt in enumerate(saved['attempts']):
        require(set(attempt)=={'index','action','feedback'} and type(attempt['index']) is int and attempt['index']==i and attempt['action'] in moves,'Illegal attempt identity/action')
        move=moves[attempt['action']]
        grade=dict(reportHash=PIN,publicationStatus=STATUS,trainerAdmission=False,evScope=source['evScope'],maximumLossBb=report['request']['maximumLossBb'],selected=move,alternatives=report['moves'])
        require(attempt['feedback']==grade,'Caller supplied grades differ')
        counts[count_fields[move['classification']]]+=1
        attempts.append(dict(index=i,action=attempt['action'],feedback=grade))
    require(saved['counts']==counts and all(type(v) is int for v in saved['counts'].values()),'Drill counts differ')
    return dict(schemaVersion='pokerlab-independent-refined-flop-drill/v1',trainerAdmission=False,sourceReportHash=PIN,attemptsChecked=len(attempts),actionsChecked=len(moves),drillStoredSha256=hashlib.sha256(raw).hexdigest(),checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),feedbackHelperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),feedbackCheck=proof)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['request','report','drill']:parser.add_argument(name,type=Path)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    require(not args.output.exists(),'Receipt output must be new');proof=check(args.request,args.report,args.drill)
    with args.output.open('x',encoding='utf-8',newline='\n') as out:out.write(json.dumps(proof,indent=2)+'\n')
    print(json.dumps(proof,indent=2))


if __name__=='__main__':main()
