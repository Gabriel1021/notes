package com.design.创建型模式.建造者模式;

/**
 * 共同部分，声明为对象
 *
 */
public class Person {

  private String head;

  private String body;

  private String foot;


  public String getHead() {
    return head;
  }

  public void setHead(String head) {
    this.head = head;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getFoot() {
    return foot;
  }

  public void setFoot(String foot) {
    this.foot = foot;
  }
}
