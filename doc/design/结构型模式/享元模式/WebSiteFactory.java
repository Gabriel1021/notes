package com.design.结构型模式.享元模式;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用工厂创建
 *
 */
public class WebSiteFactory {

  //存储实例对象
  private static Map<String, WebSite> webSites = new HashMap<String, WebSite>();

  /**
   * 禁止外部创建
   *
   */
  private WebSiteFactory() {
  }

  /**
   * type作为对象公共的属性，使用该属性获取对应的对象实例
   */
  public static WebSite createWebSite(String type) {
    WebSite webSite = webSites.get(type);

    if (webSite == null) {//没有则创建
      webSite = new ConcurrentWebSite(type);
      //添加到列表中存储
      webSites.put(type, webSite);
    }
    return webSite;
  }

  /**
   * 获取实例对象的个数
   *
   */
  public static int webSitesCount() {
    return webSites.size();
  }
}