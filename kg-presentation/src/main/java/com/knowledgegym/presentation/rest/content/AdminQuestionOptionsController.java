package com.knowledgegym.presentation.rest.content;

import com.knowledgegym.content.domain.model.QuestionOption;
import com.knowledgegym.content.domain.port.QuestionOptionRepository;
import com.knowledgegym.presentation.rest.content.dto.AdminQuestionDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/content/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionOptionsController {
    private final QuestionOptionRepository options;
    public AdminQuestionOptionsController(QuestionOptionRepository options) { this.options = options; }
    public record OptionInput(UUID id, @NotBlank String content, @NotNull Boolean isCorrect,
                              @NotNull @Min(0) Integer displayOrder) {}
    public record OptionsInput(@NotEmpty List<@Valid OptionInput> options) {}
    @PutMapping("/{id}/options")
    @CacheEvict(value = "questions", allEntries = true)
    public List<AdminQuestionDTO.AdminQuestionOptionDTO> replace(@PathVariable UUID id,
                                                                  @Valid @RequestBody OptionsInput input) {
        List<QuestionOption> requested = input.options().stream().map(value -> {
            QuestionOption option = new QuestionOption(value.content(), value.isCorrect(), value.displayOrder());
            option.setId(value.id());
            return option;
        }).toList();
        return options.saveManual(id, requested).stream()
                .map(AdminQuestionDTO.AdminQuestionOptionDTO::of).toList();
    }
}
