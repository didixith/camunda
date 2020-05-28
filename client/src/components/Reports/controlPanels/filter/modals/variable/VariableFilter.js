/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Modal, Button, Typeahead, Labeled} from 'components';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './VariableFilter.scss';
import {t} from 'translation';

export default class VariableFilter extends React.Component {
  state = {
    valid: false,
    filter: {},
    variables: [],
    selectedVariable: null,
  };

  componentDidMount = async () => {
    if (this.props.filterData) {
      const filterData = this.props.filterData.data;

      const InputComponent = this.getInputComponentForVariable(filterData);
      const filter = InputComponent.parseFilter
        ? InputComponent.parseFilter(this.props.filterData)
        : filterData.data;

      const {id, name, type} = filterData;
      this.setState({
        selectedVariable: {id, name, type},
        filter,
        valid: true,
      });
    }

    this.setState({
      variables: await this.props.config.getVariables(),
    });
  };

  selectVariable = (nameOrId) => {
    const variable = this.state.variables.find((variable) => this.getId(variable) === nameOrId);
    this.setState({
      selectedVariable: variable,
      filter: this.getInputComponentForVariable(variable).defaultFilter,
    });
  };

  getInputComponentForVariable = (variable) => {
    if (!variable) {
      return () => null;
    }

    switch (variable.type.toLowerCase()) {
      case 'string':
        return StringInput;
      case 'boolean':
        return BooleanInput;
      case 'date':
        return DateInput;
      default:
        return NumberInput;
    }
  };

  setValid = (valid) => this.setState({valid});

  changeFilter = (filter) => this.setState({filter});

  getId = (variable) => {
    if (variable) {
      return variable.id || variable.name;
    }
  };

  render() {
    const {selectedVariable, variables} = this.state;

    const ValueInput = this.getInputComponentForVariable(selectedVariable);

    return (
      <Modal open={true} onClose={this.props.close} className="VariableFilter__modal">
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.${this.props.filterType}`),
          })}
        </Modal.Header>
        <Modal.Content>
          <Labeled className="LabeledTypeahead" label={t('common.filter.variableModal.inputLabel')}>
            <Typeahead
              onChange={this.selectVariable}
              initialValue={variables.length > 0 && this.getId(selectedVariable)}
              placeholder={t('common.filter.variableModal.inputPlaceholder')}
              noValuesMessage={t('common.filter.variableModal.noVariables')}
            >
              {variables.map((variable) => (
                <Typeahead.Option key={this.getId(variable)} value={this.getId(variable)}>
                  {this.getVariableName(variable)}
                </Typeahead.Option>
              ))}
            </Typeahead>
          </Labeled>
          <ValueInput
            config={this.props.config}
            variable={selectedVariable}
            setValid={this.setValid}
            changeFilter={this.changeFilter}
            filter={this.state.filter}
          />
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={this.props.close}>
            {t('common.cancel')}
          </Button>
          <Button main primary disabled={!this.state.valid} onClick={this.createFilter}>
            {this.props.filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  getVariableName = (variable) => (variable ? variable.name : null);

  createFilter = (evt) => {
    evt.preventDefault();

    const variable = this.state.selectedVariable;
    const InputComponent = this.getInputComponentForVariable(variable);
    const {filter} = this.state;

    InputComponent.addFilter
      ? InputComponent.addFilter(this.props.addFilter, variable, filter)
      : this.props.addFilter({
          type: this.props.filterType,
          data: {
            name: variable.id || variable.name,
            type: variable.type,
            data: filter,
          },
        });
  };
}
