import React, { useMemo } from "react";
import { t } from "ttag";
import _ from "underscore";
import Button from "metabase/components/Button";
import ActiveStep from "../ActiveStep";
import InactiveStep from "../InvactiveStep";
import { Locale, LocaleData } from "../../types";
import {
  StepLocaleList,
  StepLocaleListItem,
  StepDescription,
} from "./LanguageStep.styled";

interface Props {
  locale?: Locale;
  localeData?: LocaleData[];
  isActive: boolean;
  isCompleted: boolean;
  onChangeLocale: (locale: Locale) => void;
  onSelectThisStep: () => void;
  onSelectNextStep: () => void;
}

const LanguageStep = ({
  locale,
  localeData,
  isActive,
  isCompleted,
  onChangeLocale,
  onSelectThisStep,
  onSelectNextStep,
}: Props) => {
  const locales = useMemo(() => getLocales(localeData), [localeData]);

  if (!isActive) {
    return (
      <InactiveStep
        title={t`Your language is set to ${locale?.name}`}
        label={1}
        isCompleted={isCompleted}
        onSelect={onSelectThisStep}
      />
    );
  }

  return (
    <ActiveStep title={t`What's your preferred language?`} label={1}>
      <StepDescription>
        {t`This language will be used throughout Metabase and will be the default for new users.`}
      </StepDescription>
      <StepLocaleList>
        {locales.map(item => (
          <StepLocaleListItem
            key={item.code}
            isSelected={item.code === locale?.code}
            onClick={() => onChangeLocale(item)}
          >
            {item.name}
          </StepLocaleListItem>
        ))}
      </StepLocaleList>
      <Button
        primary={locale != null}
        disabled={locale == null}
        onClick={onSelectNextStep}
      >{t`Next`}</Button>
    </ActiveStep>
  );
};

export const getLocales = (localeData: LocaleData[] = [["en", "English"]]) => {
  return _.chain(localeData)
    .map(([code, name]) => ({ code, name }))
    .sortBy(locale => locale.name)
    .value();
};

export default LanguageStep;
