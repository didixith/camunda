/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useField} from 'react-final-form';

import {isFieldValid} from 'modules/utils/isFieldValid';
import {Container, WarningIcon} from './Error.styled';

type ErrorProps = {
  name: string;
};

const Error: React.FC<ErrorProps> = ({name}) => {
  const {meta} = useField(name);

  return isFieldValid(meta) ? null : (
    <Container>
      <WarningIcon title={meta.submitError ?? meta.error} />
    </Container>
  );
};

type VariableErrorProps = {
  names: [string, string];
};

const VariableError: React.FC<VariableErrorProps> = ({names}) => {
  const [firstName, secondName] = names;
  const firstField = useField(firstName);
  const secondField = useField(secondName);

  return isFieldValid(firstField.meta) &&
    isFieldValid(secondField.meta) ? null : (
    <Container>
      <WarningIcon
        title={
          firstField.meta.submitError ??
          secondField.meta.submitError ??
          firstField.meta.error ??
          secondField.meta.error
        }
      />
    </Container>
  );
};

export {Error, VariableError};
