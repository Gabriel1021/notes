package com.design.创建型模式.工厂方法模式;

/**
 * 发送邮件工厂
 *
 */
public class SendMailFactory implements Provider {

  /**
   * 生产，创建邮件发送实例
   *
   *
   * @return (non - Javadoc)
   * @see com.zb.创建型模式.工厂方法模式.Provider#produce()
   */
  @Override
  public Sender produce() {
    return new MailSender();
  }

}

