package com.design.创建型模式.工厂方法模式;

/**
 * 发送邮件，实现Sender接口
 *
 */
public class MailSender implements Sender {

  @Override
  public void send() {
    System.out.println("这是在发送邮件...");
  }

}

