package com.design.创建型模式.工厂方法模式.简单工厂模式.多个工厂方法模式;

/**
 * 提供2个方法，分别获取 MailSender 、 SmsSender 的实例
 *
 */
public class SendFactory {


  public Sender produceMail() {
    return new MailSender();
  }


  public Sender produceSms() {
    return new SmsSender();
  }
}
