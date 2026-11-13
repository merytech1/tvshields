import assert from 'node:assert/strict';
import { test } from 'node:test';

import { TRUSTED, checkDependencies, merge, mimeOf, redirectResources, scriptletResources } from './convert.mjs';

const decode = (content) => Buffer.from(content, 'base64').toString('utf8');

function safeSelf() {
  return {};
}

function setConstant(name, value) {
  safeSelf();
  return [name, value];
}

test('scriptlets keep their function source, aliases and dependencies', () => {
  const [helper, scriptlet] = scriptletResources([
    { name: 'safe-self.fn', fn: safeSelf },
    {
      name: 'set-constant.js',
      aliases: ['set.js'],
      fn: setConstant,
      dependencies: ['safe-self.fn'],
    },
  ]);
  assert.deepEqual(helper.kind, { mime: 'fn/javascript' });
  assert.equal(helper.dependencies, undefined);
  assert.equal(scriptlet.kind, 'template');
  assert.deepEqual(scriptlet.aliases, ['set.js']);
  assert.deepEqual(scriptlet.dependencies, ['safe-self.fn']);
  assert.match(decode(scriptlet.content), /^function setConstant\(name, value\)/);
  assert.equal(scriptlet.permission, undefined);
});

test('trusted scriptlets need the trusted permission', () => {
  const [resource] = scriptletResources([{ name: 'trusted-set.js', fn: setConstant, requiresTrust: true }]);
  assert.equal(resource.permission, TRUSTED);
});

test('scriptlets that are not function declarations are rejected', () => {
  const arrow = () => {};
  assert.throws(() => scriptletResources([{ name: 'arrow.js', fn: arrow }]), /plain function/);
});

test('redirects get their MIME type and file content; parameterized ones are skipped', () => {
  const redirects = new Map([
    ['noop.js', { alias: ['noopjs', 'abp-resource:blank-js'], data: 'text' }],
    ['1x1.gif', { alias: '1x1-transparent.gif', data: 'blob' }],
    ['empty', {}],
    ['click2load.html', { params: ['aliasURL', 'url'] }],
    ['google-ima-dai.js', { requiresTrust: true }],
  ]);
  const resources = redirectResources(redirects, (name) => `content of ${name}`);
  assert.deepEqual(resources.map((r) => r.name), ['noop.js', '1x1.gif', 'empty', 'google-ima-dai.js']);
  assert.deepEqual(resources[0].kind, { mime: 'application/javascript' });
  assert.deepEqual(resources[0].aliases, ['noopjs', 'abp-resource:blank-js']);
  assert.deepEqual(resources[1].aliases, ['1x1-transparent.gif']);
  assert.deepEqual(resources[2].kind, { mime: 'text/plain' });
  assert.equal(decode(resources[0].content), 'content of noop.js');
  assert.equal(resources[3].permission, TRUSTED);
});

test('unknown file types fail the build', () => {
  assert.throws(() => mimeOf('font.woff2'), /Unknown MIME/);
});

test('merge keeps the first owner of each name and alias', () => {
  const brave = [{ name: 'noop.js', aliases: [], kind: 'template', content: '' }];
  const ublock = [
    { name: 'noop.js', aliases: ['noopjs'], kind: 'template', content: '' },
    { name: 'other.js', aliases: ['noop.js', 'o.js'], kind: 'template', content: '' },
  ];
  const { resources, dropped } = merge(brave, ublock);
  assert.deepEqual(resources.map((r) => [r.name, r.aliases]), [['noop.js', []], ['other.js', ['o.js']]]);
  assert.deepEqual(dropped, ['resource noop.js', 'alias noop.js of other.js']);
});

test('missing dependencies fail the build', () => {
  const resources = [{ name: 'a.js', aliases: [], dependencies: ['missing.fn'] }];
  assert.throws(() => checkDependencies(resources), /missing resource missing.fn/);
});
