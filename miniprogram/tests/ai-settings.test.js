const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path')
function page(request){
  let p; vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../pages/ai-settings/ai-settings.js'),'utf8'),{
    require:name=>name.includes('environment')?{billingWebUrl:'https://example.com/app/ai-settings'}:request,
    Page:v=>{p=v},getApp:()=>({checkLogin:()=>true}),wx:{showToast(){}}})
  p.setData=v=>Object.assign(p.data,v);return p
}
test('mini app reads subscription and prices without accepting model credentials',async()=>{
  const calls=[]
  const p=page({get:async url=>{calls.push(url);return url.endsWith('/access')?{allowed:true,admin:false}:{plans:[{code:'plus',amountCents:990}],message:'按月付费'}}})
  await p.load();assert.equal(p.data.status.allowed,true);assert.equal(p.data.plans[0].price,'9.90');assert.equal(p.data.apiKey,undefined)
  assert.deepEqual(calls,['/api/ai/access','/api/ai/payments/catalog'])
})
test('failed refresh clears stale paid status',async()=>{
  const p=page({get:async()=>{throw new Error('查询失败')}});p.data.status={allowed:true}
  await p.load();assert.equal(p.data.status,null);assert.equal(p.data.error,'查询失败')
})
