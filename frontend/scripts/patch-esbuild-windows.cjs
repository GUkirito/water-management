const fs = require('fs')

if (process.platform !== 'win32') {
  process.exit(0)
}

const file = require.resolve('esbuild/lib/main.js')
let source = fs.readFileSync(file, 'utf8')
const patches = [
  {
    from: `fs: fsAsync,
      callback: (err, res) => err ? reject(err) : resolve(res)`,
    to: `fs: fsSync,
      callback: (err, res) => err ? reject(err) : resolve(res)`
  },
  {
    from: 'input.length > 1024 * 1024',
    to: 'false && input.length > 1024 * 1024'
  }
]

let changed = false
for (const { from, to } of patches) {
  if (source.includes(to)) {
    continue
  }
  if (!source.includes(from)) {
    throw new Error('Unexpected esbuild API shape; patch not applied.')
  }
  source = source.replace(from, to)
  changed = true
}

// ponytail: Node 24 + esbuild 0.25.12 on Windows can fail deleting async transform temp files.
if (changed) {
  fs.writeFileSync(file, source)
}
