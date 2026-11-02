'''Generate the Android SDK catalogue from pinned public API signatures.'''

import argparse
import base64
import gzip
import hashlib
import importlib.util
import json
import logging
from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'app/src/dikciz/sdk/source.json'
POLICY = ROOT / 'app/src/dikciz/sdk/read-policy.json'
OUTPUT = ROOT / 'app/src/dikciz/assets/android-sdk'
LOGGER = logging.getLogger(__name__)
MODIFIERS = {'public', 'protected', 'static', 'final', 'abstract', 'default', 'synchronized', 'native', 'transient', 'volatile', 'strictfp'}
JAVA_LANG = {'Object', 'String', 'CharSequence', 'Class', 'Throwable', 'Exception', 'RuntimeException', 'Error', 'Void', 'Boolean', 'Byte', 'Short', 'Integer', 'Long', 'Float', 'Double', 'Character', 'Number', 'Enum', 'Comparable', 'Iterable', 'Cloneable', 'AutoCloseable', 'Runnable', 'Thread', 'StringBuilder', 'StringBuffer'}
ANNOTATION = re.compile(r'@[\w.$]+')
CLASS = re.compile(r'\b(class|interface|enum|annotationinterface)\s+([\w.$]+)')


def split_types(value):
    pieces, start, depth = [], 0, 0
    quote, escaped = None, False
    for position, char in enumerate(value):
        if quote is not None:
            if escaped:
                escaped = False
            elif char == chr(92):
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in (chr(34), chr(39)):
            quote = char
        elif char in '<([{':
            depth += 1
        elif char in '>)]}':
            depth -= 1
        elif char == ',' and depth == 0:
            pieces.append(value[start:position].strip())
            start = position + 1
    if depth or quote is not None:
        raise ValueError('Unbalanced signature type or annotation')
    if value[start:].strip():
        pieces.append(value[start:].strip())
    return pieces


def strip_annotations(value):
    result, position = [], 0
    while position < len(value):
        if value[position] != '@':
            result.append(value[position])
            position += 1
            continue
        annotation_start = position
        match = ANNOTATION.match(value, position)
        if match is None:
            raise ValueError('Invalid annotation')
        position = match.end()
        if position < len(value) and value[position] == '(':
            depth, quote, escaped = 1, False, False
            position += 1
            while position < len(value) and depth:
                char = value[position]
                if quote:
                    if escaped:
                        escaped = False
                    elif char == chr(92):
                        escaped = True
                    elif char == chr(34):
                        quote = False
                elif char == chr(34):
                    quote = True
                elif char == '(':
                    depth += 1
                elif char == ')':
                    depth -= 1
                position += 1
            if depth:
                raise ValueError('Unclosed annotation')
        result.append(' ' * (position - annotation_start))
    return ''.join(result)


def erase_generics(value):
    result, depth = [], 0
    for char in value:
        if char == '<':
            depth += 1
        elif char == '>':
            depth -= 1
        elif depth == 0:
            result.append(char)
    if depth:
        raise ValueError('Unbalanced generic type')
    return ''.join(result)


def type_variables(value):
    start = value.find('<')
    if start < 0:
        return {}
    depth, end = 1, start + 1
    while end < len(value) and depth:
        depth += (value[end] == '<') - (value[end] == '>')
        end += 1
    result = {}
    for entry in split_types(value[start + 1:end - 1]):
        parts = entry.split(' extends ', 1)
        result[parts[0].strip()] = erase_generics(parts[1].split(' & ')[0].strip()) if len(parts) == 2 else 'java.lang.Object'
    return result


def erase_type(value, variables):
    value = erase_generics(strip_annotations(value)).strip().replace('...', '[]')
    value = ' '.join(word for word in value.split() if word not in MODIFIERS)
    dimensions = ''
    while value.endswith('[]'):
        dimensions += '[]'
        value = value[:-2].strip()
    value = variables.get(value, value)
    if value in JAVA_LANG:
        value = 'java.lang.' + value
    return value + dimensions


def nullability(value):
    prefix = re.match(r'\s*(?:@[\w.$]+(?:\([^)]*\))?\s*)*', value).group()
    return True if '@Nullable' in prefix else False if '@NonNull' in prefix else None


def permission_annotations(value):
    groups = []
    for match in re.finditer(r'@RequiresPermission(?:\.(?:Read|Write))?\((.*?)\)(?:\s|$)', value):
        raw = match.group(1)
        groups.append({'raw': raw, 'mode': 'any' if 'anyOf' in raw else 'all', 'constants': re.findall(r'(?:android\.)?Manifest\.permission\.([A-Z0-9_]+)', raw), 'permissions': re.findall(chr(34) + r'(android\.permission\.[\w.]+)' + chr(34), raw), 'conditional': 'conditional=true' in raw.replace(' ', '')})
    for group in groups:
        raw = group.pop('raw')
        api_range = re.search(r'apis\s*=\s*"([^"]+)"', raw)
        if api_range:
            group['apis'] = api_range.group(1)
            raw = raw[:api_range.start()] + raw[api_range.end():]
        literals = re.findall(chr(34) + r'([^"]+)' + chr(34), raw)
        group['unresolvedRequirements'] = [value for value in literals if not value.startswith('android.permission.')]
    return groups


def parse_signatures(text):
    classes, package, current = {}, None, None
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if line.startswith('package '):
            package = line.split()[1]
            current = None
            continue
        if line == '}':
            current = None
            continue
        if not line or line.startswith('//'):
            continue
        if current is None:
            clean = strip_annotations(line.replace('@interface', 'annotationinterface'))
            match = CLASS.search(clean)
            if match is None:
                continue
            name = package + '.' + match.group(2)
            tail = erase_generics(clean[match.end():]).rstrip(' {')
            parents = []
            for separator in (' extends ', ' implements '):
                if separator in tail:
                    parents.extend(re.findall(r'[\w.$]+', tail.split(separator, 1)[1].split(' implements ', 1)[0]))
            if not parents and name != 'java.lang.Object' and match.group(1) == 'class':
                parents = ['java.lang.Object']
            current = {'name': name, 'binaryName': package + '.' + match.group(2).replace('.', '$'), 'kind': match.group(1), 'abstract': ' abstract ' in ' ' + clean, 'parents': parents, 'variables': type_variables(clean[match.end():]) if clean[match.end():].startswith('<') else {}, 'members': []}
            if name in classes:
                raise ValueError(f'Duplicate class at line {number}')
            classes[name] = current
            continue
        kind, declaration = line.split(' ', 1)
        if kind not in {'method', 'ctor', 'field', 'enum_constant'}:
            raise ValueError(f'Unknown declaration at line {number}')
        clean = strip_annotations(declaration).rstrip(';')
        member = {'kind': 'constructor' if kind == 'ctor' else 'field' if kind in {'field', 'enum_constant'} else 'method', 'public': 'public' in clean.split(), 'static': 'static' in clean.split(), 'declaration': line, 'permissions': permission_annotations(line), 'mainThread': bool(re.search(r'@(UiThread|MainThread)\b', line)), 'deprecated': '@Deprecated' in line, 'nullable': True if '@Nullable' in declaration.split('(', 1)[0] else False if '@NonNull' in declaration.split('(', 1)[0] else None}
        if member['kind'] == 'field':
            words = clean.split(' = ', 1)[0].split()
            member.update(name=words[-1], type=erase_type(erase_generics(clean.split(' = ', 1)[0]).split()[-2], current['variables']), final='final' in words)
            member['id'] = 'field:' + member['name']
            if ' = ' in clean:
                member['constant'] = clean.split(' = ', 1)[1]
        else:
            opening, closing = clean.index('('), clean.rindex(')')
            prefix = clean[:opening].strip()
            unmodified = ' '.join(word for word in prefix.split() if word not in MODIFIERS)
            variables = {**current['variables'], **type_variables(unmodified)} if unmodified.startswith('<') else current['variables']
            raw_parameters = split_types(declaration.strip().rstrip(';')[opening + 1:closing])
            parameters = [{'name': 'arg' + str(index), 'type': erase_type(argument, variables), 'nullable': nullability(argument)} for index, argument in enumerate(raw_parameters)]
            member.update(name='new' if kind == 'ctor' else prefix.split()[-1], parameters=parameters, returns=current['name'] if kind == 'ctor' else erase_type(erase_generics(prefix).split()[-2], variables))
            member['id'] = member['name'] + '(' + ','.join(parameter['type'] for parameter in parameters) + ')'
        current['members'].append(member)
    if not classes:
        raise ValueError('No SDK classes')
    java_lang = {name.removeprefix('java.lang.'): name for name in classes if name.startswith('java.lang.')}
    def normalize(value):
        dimensions = ''
        while value.endswith('[]'):
            dimensions += '[]'
            value = value[:-2]
        return java_lang.get(value, value) + dimensions
    for entry in classes.values():
        entry['parents'] = [normalize(parent) for parent in entry['parents']]
        for member in entry['members']:
            for parameter in member.get('parameters', []):
                parameter['type'] = normalize(parameter['type'])
            for key in ('returns', 'type'):
                if key in member:
                    member[key] = normalize(member[key])
            if member['kind'] != 'field':
                member['id'] = member['name'] + '(' + ','.join(parameter['type'] for parameter in member['parameters']) + ')'
    return classes


def generate(source, config, policy):
    if hashlib.sha256(source).hexdigest() != config['sha256']:
        raise ValueError('Pinned SDK source checksum mismatch')
    classes = parse_signatures(source.decode())
    unknown_policy = (set(policy.get('members', {})) | set(policy.get('fields', {}))) - set(classes)
    if unknown_policy:
        raise ValueError('Read policy has absent SDK classes: ' + str(sorted(unknown_policy)))
    constants = {member['name']: json.loads(member['constant']) for member in classes['android.Manifest.permission']['members'] if member['kind'] == 'field' and member.get('constant', '').startswith(chr(34))}
    data, index = bytearray(), []
    for name, entry in sorted(classes.items()):
        reads, fields = policy.get('members', {}).get(name, []), policy.get('fields', {}).get(name, [])
        absent = set(reads) - {member['id'] for member in entry['members']}
        if absent:
            raise ValueError('Read policy has absent SDK members: ' + name + ' ' + str(absent))
        for member in entry['members']:
            member['readOnly'] = member['id'] in reads or (member['kind'] == 'field' and ('*' in fields or member['name'] in fields or (member['public'] and member['static'] and member.get('final') and 'constant' in member)))
            for group in member['permissions']:
                group['permissions'] += [constants[key] for key in group['constants'] if key in constants]
                group['unresolvedConstants'] = [key for key in group.pop('constants') if key not in constants]
        entry.pop('variables')
        encoded = (json.dumps(entry, separators=(',', ':'), ensure_ascii=False) + '\n').encode()
        index.append({key: entry[key] for key in ('name', 'binaryName', 'kind', 'abstract', 'parents')} | {'offset': len(data), 'bytes': len(encoded), 'memberCount': len(entry['members']), 'readOnlyCount': sum(member['readOnly'] for member in entry['members'])})
        data.extend(encoded)
    metadata = {'formatVersion': 1, 'sdkInt': config['sdkInt'], 'source': config, 'dataBytes': len(data), 'dataSha256': hashlib.sha256(data).hexdigest(), 'classCount': len(index), 'memberCount': sum(entry['memberCount'] for entry in index), 'classes': index, 'policy': policy, 'policySha256': hashlib.sha256(json.dumps(policy, sort_keys=True).encode()).hexdigest()}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / 'index.json').write_text(json.dumps(metadata, separators=(',', ':')) + '\n')
    (OUTPUT / 'members.bin').write_bytes(gzip.compress(bytes(data), mtime=0))
    LOGGER.info('Android SDK catalogue generated', extra={'class_count': len(index), 'member_count': metadata['memberCount']})
    return metadata


def main():
    spec = importlib.util.spec_from_file_location('dikciz_sdk_logging', ROOT / 'scripts/dikciz-control.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.configure_logging(ROOT / '.testing/android-sdk-generation.log')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path)
    arguments = parser.parse_args()
    config, policy = json.loads(CONFIG.read_text()), json.loads(POLICY.read_text())
    if arguments.input is not None:
        source = arguments.input.read_bytes()
    else:
        request = urllib.request.Request(config['url'], headers={'User-Agent': 'dikciz-sdk-generator'})
        with urllib.request.urlopen(request, timeout=60) as response:
            encoded = response.read(20 * 1024 * 1024 + 1)
            if len(encoded) > 20 * 1024 * 1024:
                raise ValueError('SDK source exceeds its download limit')
            source = base64.b64decode(encoded, validate=True)
    generate(source, config, policy)


if __name__ == '__main__':
    main()
