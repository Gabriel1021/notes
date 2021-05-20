package com.design.创建型模式.工厂方法模式.简单工厂模式.多个静态方法模式;

/**
 * 提供2个静态方法
 *
 */
public class SendFactory {


  public static Sender produceMail() {
    return new MailSender();
  }


  public static Sender produceSms() {
    return new SmsSender();
  }
}
