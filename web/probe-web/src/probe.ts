/**
 * R1 集成验证 · TypeScript 消费端
 *
 * 这个文件的**唯一目的**是回答一个问题：
 *   Web 端（TypeScript）到底能不能真的用上 Kotlin/JS 产出的共享内核？
 *
 * 因此它必须做到「真的调用 + 断言返回值」，而不是只 import 一下。
 * 运行方式：node src/probe.ts（Node 24 原生支持类型擦除，已去掉 experimental 标记）
 * 类型检查：tsc --noEmit（同一个文件，两个关卡都过才算通过）
 *
 * 本文件同时验证**三种消费形态**，作为「TS 侧调用是否别扭」这一验收项的直接证据：
 *   A. 直接吃 Kotlin 的扁平 ESM 导出
 *   B. 经装配层的聚合对象（推荐）
 *   C. 命名空间写法（兼容 UMD 时代习惯）
 */

import { strict as assert } from 'node:assert'

// 形态 A：直接消费 Kotlin 产物（最底层，零封装）
import { echoTrimmed as flatEcho, kernelVersion as flatVersion } from '@viewphone/shared/kotlin/viewphone-shared.mjs'
// 形态 B / C：经装配层
import { com, kernel } from '@viewphone/shared'

const passed: string[] = []
function check(name: string, fn: () => void): void {
  fn()
  passed.push(name)
  console.log(`  ✓ ${name}`)
}

console.log('[probe] 验证 TypeScript 是否真的能调用 Kotlin/JS 内核\n')

console.log('形态 A · 直接消费 Kotlin 扁平导出')
check('A1 flatVersion() 返回预期版本串', () => {
  assert.equal(flatVersion(), 'viewphone-shared/0.0.1-p0')
})
check('A2 flatEcho() 真的裁剪首尾空白', () => {
  assert.equal(flatEcho('   hello world   '), 'hello world')
})

console.log('\n形态 B · 经装配层聚合对象（推荐）')
check('B1 导入的 kernel 是对象', () => {
  assert.equal(typeof kernel, 'object')
})
check('B2 kernel.kernelVersion() 返回预期版本串', () => {
  assert.equal(kernel.kernelVersion(), 'viewphone-shared/0.0.1-p0')
})
check('B3 kernel.echoTrimmed() 真的裁剪首尾空白', () => {
  assert.equal(kernel.echoTrimmed('   hello world   '), 'hello world')
})
check('B4 纯空白输入返回空串（非恒等返回）', () => {
  assert.equal(kernel.echoTrimmed('     '), '')
})
check('B5 无空白输入原样返回', () => {
  assert.equal(kernel.echoTrimmed('viewphone'), 'viewphone')
})
check('B6 中文输入正确往返（跨语言边界语义）', () => {
  assert.equal(kernel.echoTrimmed('  微光机  '), '微光机')
})

console.log('\n形态 C · 命名空间写法（兼容 UMD 时代）')
check('C1 com.viewphone.shared.api.kernelVersion() 可用', () => {
  assert.equal(com.viewphone.shared.api.kernelVersion(), 'viewphone-shared/0.0.1-p0')
})
check('C2 com 命名空间与 kernel 指向同一实现', () => {
  assert.equal(com.viewphone.shared.api.echoTrimmed(' x '), kernel.echoTrimmed(' x '))
})

console.log(`\n[probe] 通过 ${passed.length} 项断言：TS 真的调用了内核并拿到正确返回值`)
