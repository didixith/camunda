/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList, Deleter} from 'components';

import PublishModal from './PublishModal';
import {loadProcesses} from './service';
import EventsWithErrorHandling from './Events';

const Events = EventsWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadProcesses: jest.fn().mockReturnValue([
    {
      id: 'process1',
      name: 'First Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'mapped'
    },
    {
      id: 'process2',
      name: 'Second Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'published'
    },
    {
      id: 'process3',
      name: 'Third Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'unpublished_changes'
    }
  ]),
  removeProcess: jest.fn()
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should load event based processes', () => {
  shallow(<Events {...props} />);

  expect(loadProcesses).toHaveBeenCalled();
});

it('should pass a process to the Deleter', () => {
  const node = shallow(<Events {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[2].action();

  expect(node.find(Deleter).prop('entity').id).toBe('process1');
});

it('should pass a process id to the PublishModal', () => {
  const node = shallow(<Events {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[0].action();

  expect(node.find(PublishModal).prop('id')).toBe('process1');
  expect(node.find(PublishModal).prop('republish')).toBe(false);
});

it('should correctly set the republish prop on the PublishModal', () => {
  const node = shallow(<Events {...props} />);

  node
    .find(EntityList)
    .prop('data')[2]
    .actions[0].action();

  expect(node.find(PublishModal).prop('republish')).toBe(true);
});
