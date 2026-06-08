package io.qml4j.demo;
import io.qml4j.engine.QmlEngine;
import io.qml4j.render.*;
import io.qml4j.render.items.core.Item;
import io.qml4j.runtime.member.MemberAccess;
import io.qml4j.runtime.invoke.MethodInvocation;
import io.github.humbleui.skija.*;
import java.nio.charset.StandardCharsets;import java.nio.file.*;
final class DlgProbe {
  public static void main(String[] a) throws Exception {
    String prop=a[0]; String setProp=a.length>1?a[1]:null; long setVal=a.length>2?Long.parseLong(a[2]):0;
    AppResourceLoader loader=new AppResourceLoader();
    QmlView v=QmlView.withStockTypes(new QmlEngine()).resources(loader);
    v.context("AppFeatures",AppFeaturesMap.all());v.context("HotReloadEnabled",Boolean.FALSE);v.context("ProjectSourceDir","");
    byte[] b=loader.load("pages/ComponentsPage.qml");
    v.load(new String(b,StandardCharsets.UTF_8),"pages");
    int w=900,h=900;v.root().x.set(0);v.root().y.set(0);v.root().width.set(w);v.root().height.set(h);
    Item tabs=find(v.root(),"currentIndex"); if(tabs!=null) MemberAccess.writeMember(tabs,"currentIndex",2L);
    Surface s=Surface.makeRasterN32Premul(w,h);AppPng.AppShotBackend bk=new AppPng.AppShotBackend(s,w,h);
    for(int i=0;i<20;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);}
    Item dlg=find(v.root(),prop);
    if(dlg!=null){ System.out.println("found "+dlg.getClass().getSimpleName()); MethodInvocation.callMethod(dlg,"open",new Object[0]); }
    for(int i=0;i<25;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);}
    if(setProp!=null && dlg!=null){ MemberAccess.writeMember(dlg,setProp,setVal); }
    for(int i=0;i<25;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);}
    Files.write(java.nio.file.Path.of("/tmp/dlg.png"),s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG).getBytes());
    System.out.println("wrote /tmp/dlg.png");
  }
  static Item find(Item it,String p){if(MemberAccess.hasProperty(it,p))return it;for(Item c:it.children){Item r=find(c,p);if(r!=null)return r;}return null;}
}
