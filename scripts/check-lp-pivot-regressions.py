"""Reproduce exact binary64 basis/direction diagnostics; never grants solver authority."""
import argparse
import gzip
from fractions import Fraction
import hashlib
import json
from pathlib import Path


def require(condition):
    if not condition:
        raise ValueError('Exact pivot regression evidence differs')


def reduced(matrix):
    a = [list(map(Fraction, row)) for row in matrix]
    n = len(a)
    pivots = []
    for col in range(n):
        chosen = next((i for i in range(len(pivots), n) if a[i][col]), None)
        if chosen is None:
            continue
        row = len(pivots)
        a[row], a[chosen] = a[chosen], a[row]
        pivot = a[row][col]
        a[row] = [v / pivot for v in a[row]]
        for i in range(n):
            if i != row and a[i][col]:
                factor = a[i][col]
                a[i] = [x - factor * y for x, y in zip(a[i], a[row], strict=True)]
        pivots.append(col)
    return a, pivots


def check(path):
    with path.open('rb') as stream:
        raw = stream.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError('Diagnostic input exceeds cap')
    if path.suffix == '.gz':
        with gzip.open(path, 'rb') as stream:
            expanded = stream.read(8 * 1024 * 1024 + 1)
    else:
        expanded = raw
    if len(expanded) > 8 * 1024 * 1024:
        raise ValueError('Expanded diagnostic input exceeds cap')
    data = json.loads(expanded)
    matrix = data['originalBasis']
    n = len(matrix)
    require(0 < n <= 512 and all(len(row) == n for row in matrix))
    a, pivots = reduced(matrix)
    free = [i for i in range(n) if i not in pivots]
    require(free)
    null = [Fraction(0)] * n
    null[free[0]] = 1
    for i, col in enumerate(pivots):
        null[col] = -a[i][free[0]]
    require(any(null))
    require(all(sum(Fraction(x) * y for x, y in zip(row, null, strict=True)) == 0 for row in matrix))
    result = dict(inputStoredBytesSha256=hashlib.sha256(raw).hexdigest(),
                  dimension=n, exactRank=len(pivots), exactNullProductsChecked=n)
    if 'priorBasisMatrix' in data:
        old = data['priorBasisMatrix']
        rhs = list(map(Fraction, data['enteringColumn']))
        augmented = [[Fraction(v) for v in row] + [b] for row, b in zip(old, rhs, strict=True)]
        for k in range(n):
            chosen = next(i for i in range(k, n) if augmented[i][k])
            augmented[k], augmented[chosen] = augmented[chosen], augmented[k]
            for i in range(k + 1, n):
                if augmented[i][k]:
                    factor = augmented[i][k] / augmented[k][k]
                    for j in range(k, n + 1):
                        augmented[i][j] -= factor * augmented[k][j]
        direction = [Fraction(0)] * n
        for i in range(n - 1, -1, -1):
            direction[i] = (augmented[i][n] - sum(augmented[i][j] * direction[j] for j in range(i + 1, n))) / augmented[i][i]
        require(all(sum(Fraction(x) * y for x, y in zip(row, direction, strict=True)) == b for row, b in zip(old, rhs, strict=True)))
        leaving = data['leavingRow']
        require(direction[leaving] == 0)
        require(data['computedDirection'][leaving] > data['directionEligibilityEps'])
        result.update(priorBasisExactRank=n, exactDirectionResidualRowsChecked=n,
                      exactLeavingDirection='0', computedLeavingDirection=data['computedDirection'][leaving],
                      eligibilityEps=data['directionEligibilityEps'])
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs', nargs='+', type=Path)
    args = parser.parse_args()
    print(json.dumps(dict(scope='EXACT_RATIONAL_DIAGNOSTIC_NOT_QUALIFICATION', trainerAdmission=False,
                          checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                          checks=[check(path) for path in args.inputs]), indent=2))
