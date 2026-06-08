package io.qml4j.demo;
import io.qml4j.engine.QmlEngine;
import io.qml4j.render.*;
import io.qml4j.render.items.core.Item;
import io.qml4j.render.items.layout.Flow;
import io.qml4j.runtime.member.MemberAccess;
import io.github.humbleui.skija.*;
import java.nio.charset.StandardCharsets;
final class LegProbe {
  public static void main(String[] a) throws Exception {
    AppResourceLoader loader=new AppResourceLoader();
    QmlView v=QmlView.withStockTypes(new QmlEngine()).resources(loader);
    v.context("AppFeatures",AppFeaturesMap.all());v.context("HotReloadEnabled",Boolean.FALSE);v.context("ProjectSourceDir","");
    byte[] b=loader.load("pages/ComponentsPage.qml");
    v.load(new String(b,StandardCharsets.UTF_8),"pages");
    int w=900,h=900;v.root().x.set(0);v.root().y.set(0);v.root().width.set(w);v.root().height.set(h);
    Item tabs=find(v.root(),"currentIndex"); if(tabs!=null) MemberAccess.writeMember(tabs,"currentIndex",3L);
    Surface s=Surface.makeRasterN32Premul(w,h);AppPng.AppShotBackend bk=new AppPng.AppShotBackend(s,w,h);
    for(int i=0;i<40;i++){s.getCanvas().clear(0xFFFFFFFF);v.renderFrame(bk);Thread.sleep(12);}
    int[] n={0};
    dump(v.root(),n);
  }
  static void dump(Item it,int[] n){
    if(it instanceof Flow){
      Flow f=(Flow)it;
      System.out.printf("Flow#%d w=%.0f h=%.0f implW=%.0f kids=%d%n",n[0]++,f.width.peekFloat(),f.height.peekFloat(),f.implicitWidth.peekFloat(),f.children.size());
      for(Item c:f.children) System.out.printf("   chip %s w=%.0f h=%.0f x=%.0f y=%.0f implW=%.0f%n",c.getClass().getSimpleName(),c.width.peekFloat(),c.height.peekFloat(),c.x.peekFloat(),c.y.peekFloat(),c.implicitWidth.peekFloat());
    }
    for(Item c:it.children) dump(c,n);
  }
  static Item find(Item it,String p){if(MemberAccess.hasProperty(it,p))return it;for(Item c:it.children){Item r=find(c,p);if(r!=null)return r;}return null;}
}
