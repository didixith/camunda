/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Input} from 'components';
import './Switch.scss';

export default function Switch(props) {
  return (
    <label
      title={props.title}
      className={classnames('Switch', {withLabel: props.label}, props.className)}
    >
      <Input type="checkbox" {...props} className="Switch__Input" />
      <span className={classnames('Switch__Slider--round', {disabled: props.disabled})} />
      {props.label && <span className="label">{props.label}</span>}
    </label>
  );
}
