const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path')
function page(request){
  let p; vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../pages/ai-settings/ai-settings.js'),'utf8'),{
    require:()=>request,Page:v=>{p=v},getApp:()=>({checkLogin:()=>true}),wx:{showToast(){}}})
  p.setData=v=>Object.assign(p.data,v);return p
}
test('mini settings clear keys after save and when leaving without persisting to storage',async()=>{
  let sent;const p=page({get:async()=>({configured:true,provider:'deepseek',model:'m'}),put:async(u,d)=>{sent=d}})
  p.data.apiKey='sk-private';p.data.embeddingApiKey='sk-vector';await p.save()
  assert.equal(sent.apiKey,'sk-private');assert.equal(p.data.apiKey,'');assert.equal(p.data.embeddingApiKey,'')
  p.data.apiKey='unsaved';p.onHide();assert.equal(p.data.apiKey,'')
})
