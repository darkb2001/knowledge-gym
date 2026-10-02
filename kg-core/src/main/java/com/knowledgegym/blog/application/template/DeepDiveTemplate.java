package com.knowledgegym.blog.application.template;

public final class DeepDiveTemplate extends BlogTemplate {
    public String name() { return "DEEP_DIVE"; }
    protected String structure() { return "Use What, Why, How, Example, Pitfalls, Interview Takeaways sections. Include code only when supported by evidence; the application appends a verified source list."; }
}
