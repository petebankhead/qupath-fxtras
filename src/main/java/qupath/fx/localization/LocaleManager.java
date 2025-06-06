/**
 * Copyright 2023 The University of Edinburgh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qupath.fx.localization;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;
import qupath.fx.utils.converters.LocaleConverter;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Manage available locales, with consistent display and string conversion.
 * This is useful to support presenting locales to a user for selection.
 * <p>
 * The implementation of this class assumes that the locales will always be shown to the user in the same language
 * for consistency, which by default is US English. This can be changed by specifying a different base locale.
 */
public class LocaleManager {

    private final Locale baseLocale;

    private final Map<String, Locale> localeMap = new TreeMap<>();
    private final StringConverter<Locale> converter;

    private final boolean useCountryCodes;

    private final ObjectProperty<Predicate<Locale>> availableLanguagePredicateProperty = new SimpleObjectProperty<>();

    private final ObservableList<Locale> allLocales;
    private ObservableList<Locale> availableLocales;

    /**
     * Create a new locale manager using the default US base locale and considering locales identified by the JVM
     * as being available.
     */
    public LocaleManager() {
        this(l -> true);
    }

    /**
     * Create a new locale manager using the default US base locale and specified predicate to determine which locales
     * should be available to the user, applied to the list of all locales identified by the JVM.
     * @param availableLocalesPredicate filter to apply to select which locales are available.
     */
    public LocaleManager(Predicate<Locale> availableLocalesPredicate) {
        this(availableLocalesPredicate, true);
    }

    /**
     * Create a new locale manager using the default US base locale and specified predicate to determine which locales
     * should be available to the user, applied to the list of all locales identified by the JVM.
     * @param availableLocalesPredicate filter to apply to select which locales are available.
     * @param useCountryCodes if true, all locales are considered; if false, locales with specific country codes are
     *                        skipped.
     */
    public LocaleManager(Predicate<Locale> availableLocalesPredicate, boolean useCountryCodes) {
        this(Locale.US, availableLocalesPredicate, useCountryCodes);
    }

    /**
     * Create a new locale manager using the specified base locale and predicate to determine which locales
     * should be available to the user, applied to the list of all locales identified by the JVM.
     * @param baseLocale the base locale to use when determining the language to display locales to the user.
     * @param availableLocalesPredicate filter to apply to select which locales are available.
     */
    public LocaleManager(Locale baseLocale, Predicate<Locale> availableLocalesPredicate) {
        this(baseLocale, availableLocalesPredicate, true);
    }

    /**
     * Create a new locale manager using the specified base locale and predicate to determine which locales
     * should be available to the user, applied to the list of all locales identified by the JVM.
     * @param baseLocale the base locale to use when determining the language to display locales to the user.
     * @param availableLocalesPredicate filter to apply to select which locales are available.
     * @param useCountryCodes if true, all locales are considered; if false, locales with specific country codes are
     *                        skipped.
     */
    public LocaleManager(Locale baseLocale, Predicate<Locale> availableLocalesPredicate, boolean useCountryCodes) {
        Objects.requireNonNull(baseLocale, "Base locale cannot be null");
        this.baseLocale = baseLocale;
        this.useCountryCodes = useCountryCodes;
        this.availableLanguagePredicateProperty.set(availableLocalesPredicate);
        initializeLocaleMap();
        converter = new LocaleConverter(baseLocale);
        allLocales = FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(localeMap.values()));
        createAvailableLocaleList();
    }

    private void initializeLocaleMap() {
        for (var locale : Locale.getAvailableLocales()) {
            if (!localeFilter(locale))
                continue;
            var name = locale.getDisplayName(baseLocale);
            localeMap.putIfAbsent(name, locale);
        }
    }

    private boolean localeFilter(Locale locale) {
        if (Objects.equals(locale, baseLocale))
            return true;
        return !locale.getLanguage().isBlank() &&
                (useCountryCodes || locale.getCountry().isEmpty()) &&
                locale != Locale.ENGLISH;
    }

    /**
     * Get an observable list of all locales identified by the JVM.
     * This may optionally exclude country-specific locales, depending upon the value of {@link #useCountryCodes()}.
     * @return
     */
    public ObservableList<Locale> getAllLocales() {
        return allLocales;
    }

    /**
     * Query whether country codes are considered.
     * If this returns false, locales with a non-empty country code will be ignored completely
     * (and will not be returned by calls to {@link #getAllLocales()}).
     * @return true if locales with non-empty country codes may be used, false otherwise
     */
    public boolean useCountryCodes() {
        return useCountryCodes;
    }

    /**
     * Get an observable list of all locales identified by the JVM that match the current predicate.
     * @return
     * @ImplNote This is not necessarily implemented as a filtered list, because locales may be refreshed at runtime,
     *           without any changes to the predicate.
     */
    public ObservableList<Locale> getAvailableLocales() {
        return availableLocales;
    }

    private void createAvailableLocaleList() {
        // We don't use a filtered list, because there were problems with 1) refreshing the backing list, and
        // 2) refreshing the predicate
        availableLocales = FXCollections.observableArrayList();
        refreshAvailableLocales();
        availableLanguagePredicateProperty.addListener((v, o, n) -> refreshAvailableLocales());
    }

    /**
     * Refresh the list of available locales, based on the current predicate.
     * This is useful if the application supports installing locales at runtime.
     */
    public void refreshAvailableLocales() {
        var predicate = availableLanguagePredicateProperty.get();
        List<Locale> newContents;
        if (predicate == null)
            newContents = allLocales;
        else
            newContents = allLocales.filtered(predicate);
        if (availableLocales.size() != newContents.size() || !new HashSet<>(availableLocales).containsAll(newContents))
            availableLocales.setAll(newContents);
    }

}