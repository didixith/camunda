import React from 'react';
import {mount} from 'enzyme';

import Alerts from './Alerts';
import {loadAlerts, loadReports} from './service';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Button: props => <button {...props}>{props.children}</button>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

jest.mock('./service', () => {
  return {
    loadAlerts: jest.fn(),
    loadReports: jest.fn(),
    saveNewAlert: jest.fn(),
    deleteAlert: jest.fn(),
    updateAlert: jest.fn()
  };
});

jest.mock('./AlertModal', () => props => (
  <span>
    EditModal: <span id="ModalProps">{JSON.stringify(props)}</span>
  </span>
));

jest.mock('services', () => {
  return {
    formatters: {
      duration: () => '14 seconds',
      frequency: () => '12'
    }
  };
});

const reports = [
  {id: '1', data: {visualization: 'table', view: {property: 'frequency'}}, name: 'Report 1'},
  {id: '2', data: {visualization: 'number', view: {property: 'duration'}}, name: 'Report 2'}
];
loadReports.mockReturnValue(reports);

loadAlerts.mockReturnValue([]);

it('should load existing reports', async () => {
  await mount(<Alerts />);

  expect(loadReports).toHaveBeenCalled();
});

it('should only save single number reports', async () => {
  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(node.state('reports').map(report => report.id)).toEqual(['2']);
});

it('should load existing alerts initially', async () => {
  const node = await mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(loadAlerts).toHaveBeenCalled();
});

it('should include an edit/add alert modal after reports are loaded', async () => {
  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(node).toIncludeText('EditModal');
});

it('should display a loading message while content is loading', () => {
  const node = mount(<Alerts />);

  expect(node.find('.sk-circle').exists()).toBe(true);
});

it('should show a message when no alerts are defined', async () => {
  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(node).toIncludeText('You have no Alerts configured yet.');
});

it('should pass an alert configuration to the alert edit modal', async () => {
  const alert = {id: '1', name: 'preconfigured alert', reportId: '2'};
  loadAlerts.mockReturnValue([alert]);

  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  // some enzyme bug causes update not to fire sometime :/
  // https://github.com/airbnb/enzyme/issues/1233#issuecomment-343449560
  node.update();

  node.find('button.Alert__editButton').simulate('click');

  expect(node.find('#ModalProps')).toIncludeText('preconfigured alert');
});

it('should format durations with value and unit', async () => {
  const node = mount(<Alerts />);

  await node.instance().componentDidMount();

  expect(node).toIncludeText('14 seconds');
});
