package com.design.行为型模式.类和类之间的关系.观察者模式;

import java.util.Enumeration;
import java.util.Vector;

public abstract class AbstractSubject implements Subject {

  private Vector<Observer> vector = new Vector<Observer>();

  /**
   * 添加观察者
   *
   *
   * @param observer (non-Javadoc)
   * @see com.zb.行为型模式.类和类之间的关系.观察者模式.Subject#add(com.zb.行为型模式.类和类之间的关系.观察者模式.Observer)
   */
  @Override
  public void add(Observer observer) {
    vector.add(observer);
  }


  /**
   * 删除观察者
   *
   *
   * @param observer (non-Javadoc)
   * @see com.zb.行为型模式.类和类之间的关系.观察者模式.Subject#del(com.zb.行为型模式.类和类之间的关系.观察者模式.Observer)
   */
  @Override
  public void del(Observer observer) {
    vector.remove(observer);
  }

  /**
   * 通知观察者
   *
   *
   * @see com.zb.行为型模式.类和类之间的关系.观察者模式.Subject#notifyObservers()
   */
  @Override
  public void notifyObservers() {
    Enumeration<Observer> enumo = vector.elements();
    while (enumo.hasMoreElements()) {
      enumo.nextElement().update();
    }
  }
}