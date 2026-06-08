package io.qml4j.demo;
import io.qml4j.engine.QmlEngine;
import io.qml4j.render.*;
import io.qml4j.render.items.core.Item;
import io.qml4j.runtime.member.MemberAccess;
import io.github.humbleui.skija.*;
import java.nio.charset.StandardCharsets;import java.nio.file.*;
final class CompProbe {
  public static void main(String[] a) throws Exception {
    int idx=Integer.parseInt(a[0]); float sy=a.length>1?Float.parseFloat(a[1]):0;
    AppResourceLoader loader=new AppResourceLoader();
    QmlView v=QmlView.withStockTypes(new QmlEngine()).resources(loader);
    v.context("AppFeatures",AppFeaturesMap.all());v.context("HotReloadEnabled",Boolean.FALSE);v.context("ProjectSourceDir","");
    byte[] b=loader.load("pages/ComponentsPage.qml");
    v.load(new String(b,StandardCharsets.UTF_8),"pages");
    int w=900,h=900; v.root().x.set(0);v.root().y.set(0);v.root().width.set(w);v.root().height.set(h);
    Item tabs=find(v.root(),"currentIndex"); if(tabs!=null) MemberAccess.writeMember(tabs,"currentIndex",(long)idx);
    Surface s=Surface.makeRasterN32Premul(w,h); AppPng.AppShotBackend bk=new AppPng.AppShotBackend(s,w,h);
    for(int i=0;i<30;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);}
    if(sy>0){ io.qml4j.render.items.core.Flickable f=flick(v.root()); if(f!=null) f.contentY.set(sy); for(int i=0;i<10;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);} }
    Files.write(java.nio.file.Path.of("/tmp/comp-"+idx+".png"),s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG).getBytes());
    System.out.println("wrote /tmp/comp-"+idx+".png");
  }
  static Item find(Item it,String p){if(MemberAccess.hasProperty(it,p))return it;for(Item c:it.children){Item r=find(c,p);if(r!=null)return r;}return null;}
  static io.qml4j.render.items.core.Flickable flick(Item it){io.qml4j.render.items.core.Flickable best=null;java.util.Deque<Item> q=new java.util.ArrayDeque<>();q.add(it);while(!q.isEmpty()){Item x=q.poll();if(x instanceof io.qml4j.render.items.core.Flickable){io.qml4j.render.items.core.Flickable f=(io.qml4j.render.items.core.Flickable)x;if(best==null||f.contentHeight.peek().doubleValue()>best.contentHeight.peek().doubleValue())best=f;}q.addAll(x.children);}return best;}
}
