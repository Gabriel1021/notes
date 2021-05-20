package com.design.创建型模式.工厂方法模式;

/**
 * 发送短信工厂
 *
 */
public class SendSmsFactory implements Provider {

  /**
   * 生产，创建短信发送实例
   *
   *
   * @return (non - Javadoc)
   * @see com.zb.创建型模式.工厂方法模式.Provider#produce()
   */
  @Override
  public Sender produce() {
    return new SmsSender();
  }

}

