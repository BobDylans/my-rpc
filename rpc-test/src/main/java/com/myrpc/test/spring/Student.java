package com.myrpc.test.spring;


public class Student {
    private String name;
    private int age;
    private double score; // 成绩 0-100

    public Student(String name, int age, double score) {
        this.name = name;
        this.age = age;
        this.score = score;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    // getter / setter / toString 请自行生成（IDE 自动生成即可）
    // 为了简洁，这里省略了，但你需要补全。
}
