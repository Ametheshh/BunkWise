package com.example.mini_project;

public class Semester {
    public int id;
    public String name;
    public double credits;   // total credits of the semester
    public double weighted;  // sum of (credit x grade point)

    public Semester(int id, String name, double credits, double weighted) {
        this.id = id;
        this.name = name;
        this.credits = credits;
        this.weighted = weighted;
    }

    public double sgpa() {
        return credits == 0 ? 0 : weighted / credits;
    }
}
