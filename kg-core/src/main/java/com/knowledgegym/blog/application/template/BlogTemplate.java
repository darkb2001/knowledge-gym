package com.knowledgegym.blog.application.template;

import com.knowledgegym.blog.domain.port.AiWriterPort;
import java.util.List;

public abstract class BlogTemplate {
    public abstract String name();
    protected abstract String structure();
    public AiWriterPort.Completion draft(AiWriterPort writer, String topic, String angle, List<AiWriterPort.Source> sources) {
        return writer.draft(topic, angle, sources, structure());
    }
}
