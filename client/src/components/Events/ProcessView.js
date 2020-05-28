/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {
  LoadingIndicator,
  Button,
  Icon,
  Deleter,
  BPMNDiagram,
  ModificationInfo,
  MessageBox,
} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError, addNotification} from 'notifications';

import ProcessRenderer from './ProcessRenderer';
import PublishModal from './PublishModal';
import {removeProcess, cancelPublish, loadProcess} from './service';
import {checkDeleteConflict} from 'services';
import {getOptimizeVersion} from 'config';

import './ProcessView.scss';

export default withErrorHandling(
  class ProcessView extends React.Component {
    state = {
      data: null,
      deleting: null,
      publishing: null,
      isPublishing: false,
      optimizeVersion: 'latest',
    };

    async componentDidMount() {
      this.load();
      this.setupPoll();

      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;

      this.setState({
        optimizeVersion: version.join('.'),
      });
    }

    componentWillUnmount() {
      this.teardownPoll();
    }

    setupPoll = () => {
      this.poll = setInterval(this.loadIfNecessary, 5000);
    };

    teardownPoll = () => {
      clearInterval(this.poll);
    };

    loadIfNecessary = async () => {
      const {data} = this.state;
      if (data && data.state === 'publish_pending') {
        await this.load();
        if (this.state.data.state === 'published') {
          addNotification({
            type: 'success',
            text: t('events.publishSuccess', {name: this.state.data.name}),
          });
        }
      }
    };

    load = () => {
      return new Promise((resolve, reject) => {
        this.props.mightFail(
          loadProcess(this.props.id),
          (data) => this.setState({data}, resolve),
          (error) => reject(showError(error))
        );
      });
    };

    cancelPublish = () => {
      this.props.mightFail(cancelPublish(this.props.id), this.load, showError);
    };

    render() {
      if (!this.state.data) {
        return <LoadingIndicator />;
      }

      const {
        deleting,
        publishing,
        optimizeVersion,
        data: {id, name, xml, mappings, state, publishingProgress, lastModified, lastModifier},
      } = this.state;

      const isPublishing = state === 'publish_pending';
      const canPublish = state === 'mapped' || state === 'unpublished_changes';
      const docsLink = `https://docs.camunda.org/optimize/${optimizeVersion}/user-guide/event-based-processes`;

      return (
        <div className="ProcessView">
          <div className="header">
            <div className="head">
              <div className="name-container">
                <h1 className="name">{name}</h1>
              </div>
              <div className="tools">
                {isPublishing && (
                  <>
                    <span className="progressLabel">
                      {t('events.state.publish_pending', {publishingProgress})}
                    </span>
                    <Button onClick={this.cancelPublish} className="tool-button cancel-button">
                      <Icon type="cancel" />
                      {t('events.cancelPublish')}
                    </Button>
                  </>
                )}
                <Link className="tool-button edit-button" to="edit">
                  <Button main disabled={isPublishing} tabIndex="-1">
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Button>
                </Link>
                <Button
                  main
                  disabled={isPublishing || !canPublish}
                  onClick={() => this.setState({publishing: id})}
                  className="tool-button publish-button"
                >
                  <Icon type="publish" />
                  {t('events.publish')}
                </Button>
                <Button
                  main
                  disabled={isPublishing}
                  onClick={() => this.setState({deleting: {id, name}})}
                  className="tool-button delete-button"
                >
                  <Icon type="delete" />
                  {t('common.delete')}
                </Button>
              </div>
            </div>
            <ModificationInfo user={lastModifier} date={lastModified} />
            {this.props.generated && (
              <MessageBox type="warning">
                {t('events.generationWarning')}{' '}
                <a href={docsLink} target="_blank" rel="noopener noreferrer">
                  {t('common.seeDocs')}
                </a>
              </MessageBox>
            )}
          </div>

          <BPMNDiagram xml={xml}>
            <ProcessRenderer mappings={mappings} />
          </BPMNDiagram>
          <Deleter
            type="process"
            descriptionText={t('events.deleteWarning', {
              name: (deleting && deleting.name) || '',
            })}
            entity={deleting}
            onDelete={this.props.onDelete}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={({id}) => removeProcess(id)}
            checkConflicts={({id}) => checkDeleteConflict(id, 'eventBasedProcess')}
          />
          {publishing && (
            <PublishModal
              id={publishing}
              onPublish={this.load}
              onClose={() => this.setState({publishing: null})}
              republish={state === 'unpublished_changes'}
            />
          )}
        </div>
      );
    }
  }
);
