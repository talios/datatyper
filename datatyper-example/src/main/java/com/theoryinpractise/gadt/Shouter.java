package com.theoryinpractise.gadt;

public interface Shouter {

  default void shout() {
    System.out.println("WOAH NELLY!");
  }
}
