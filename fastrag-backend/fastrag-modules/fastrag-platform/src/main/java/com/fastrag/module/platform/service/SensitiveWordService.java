package com.fastrag.module.platform.service;

import com.fastrag.module.platform.entity.SensitiveWord;
import java.util.List;
import java.util.Optional;

public interface SensitiveWordService {

    /**
     * Query all enabled sensitive words from DB.
     */
    List<SensitiveWord> list();

    /**
     * Invalidate the enabled-words cache; must be called after any write
     * (create/update/delete/batch import) so chat-time filtering sees fresh data.
     */
    void invalidate();

    /**
     * Core filtering logic.
     * @param text input text to check
     * @param mode one of "reject", "replace", "mask"
     */
    Optional<String> checkAndFilter(String text, String mode);

    /**
     * Check only blockInput category words. Returns error message if matched.
     */
    Optional<String> checkInput(String text);

    /**
     * Check only replaceAnswer category words. Returns filtered text if matched.
     */
    Optional<String> filterOutput(String text);

    /** Save a new sensitive word. */
    void save(SensitiveWord sw);

    /** Update an existing sensitive word. */
    void update(SensitiveWord sw);

    /** Delete a sensitive word by id. */
    void delete(Long id);

    /** Get a sensitive word by id. */
    SensitiveWord getById(Long id);
}
