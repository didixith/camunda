import {jsx} from 'view-utils';
import sinon from 'sinon';
import {expect} from 'chai';
import {mountTemplate, setupPromiseMocking, triggerEvent, createMockComponent} from 'testHelpers';
import {License, __set__, __ResetDependency__} from 'license/License';

describe('<License>', () => {
  const license = 'some awesome license!';
  let Header;
  let Footer;
  let Notifications;
  let get;
  let post;
  let node;
  let unlimitedLicenseResponse;
  let limitedLicenseResponse;
  let $window;

  setupPromiseMocking();

  beforeEach(() => {
    Header = createMockComponent('Header');
    Footer = createMockComponent('Footer');
    Notifications = createMockComponent('Notifications');
    get = sinon.stub();
    post = sinon.stub();

    unlimitedLicenseResponse = {
      customerId: 'schrottis inn',
      validUntil: null,
      unlimited: true
    };

    limitedLicenseResponse = {
      customerId: 'schrottis inn',
      validUntil: '9999-12-31T00:00:00',
      unlimited: false
    };

    $window = {
      setTimeout: sinon.stub().callsArg(0),
      location: {}
    };

    __set__('Header', Header);
    __set__('Footer', Footer);
    __set__('Notifications', Notifications);
    __set__('get', get);
    __set__('post', post);
    __set__('$window', $window);
  });

  afterEach(() => {
    __ResetDependency__('Header');
    __ResetDependency__('Footer');
    __ResetDependency__('Notifications');
    __ResetDependency__('get');
    __ResetDependency__('post');
    __ResetDependency__('$window');
  });

  describe('with invalid license', () => {
    const errorMessage = 'some awesome error message!';

    beforeEach(() => {
      get.returns(
        Promise.reject({
          json: sinon.stub().returns(Promise.resolve({
            errorMessage
          }))
        })
      );

      ({node} = mountTemplate(<License />));
    });

    it('should display error message', () => {
      Promise.runAll();

      expect(node.querySelector('.alert')).to.contain.text(errorMessage);
      expect(node.querySelector('.alert')).not.to.have.class('hidden');
    });

    it('should display valid license when storing new unlimited license success', () => {
      post.returns(
        Promise.resolve({
          json: sinon.stub().returns(Promise.resolve(unlimitedLicenseResponse))
        })
      );

      Promise.runAll(); // run initial successful promise

      node.querySelector('textarea').value = license;

      triggerEvent({
        node,
        selector: 'form',
        eventName: 'submit'
      });

      Promise.runAll();

      expect(node.querySelector('.alert')).to.contain.text('Licensed for schrottis inn.');
      expect(node.querySelector('.alert')).not.to.have.class('hidden');
    });

    it('should display valid license when storing new limited license success', () => {
      post.returns(
        Promise.resolve({
          json: sinon.stub().returns(Promise.resolve(limitedLicenseResponse))
        })
      );

      Promise.runAll(); // run initial successful promise

      node.querySelector('textarea').value = license;

      triggerEvent({
        node,
        selector: 'form',
        eventName: 'submit'
      });

      Promise.runAll();

      expect(node.querySelector('.alert')).to.contain.text('Licensed for schrottis inn. Valid until 9999-12-31');
      expect(node.querySelector('.alert')).not.to.have.class('hidden');
    });

    it('should redirected to main page after timeout', () => {
      post.returns(
        Promise.resolve({
          json: sinon.stub().returns(Promise.resolve(limitedLicenseResponse))
        })
      );

      Promise.runAll(); // run initial successful promise

      node.querySelector('textarea').value = license;

      triggerEvent({
        node,
        selector: 'form',
        eventName: 'submit'
      });

      Promise.runAll();

      expect($window.setTimeout.called).to.eql(true);
      expect($window.location.pathname).to.eql('/');
    });
  });

  describe('with valid license', () => {
    beforeEach(() => {
      get.returns(
        Promise.resolve({
          json: sinon.stub().returns(Promise.resolve(unlimitedLicenseResponse))
        })
      );

      ({node} = mountTemplate(<License />));
    });

    it('should display Header', () => {
      expect(node).to.contain.text(Header.text);
    });

    it('should display Footer', () => {
      expect(node).to.contain.text(Footer.text);
    });

    it('should display notifications', () => {
      expect(node).to.contain.text(Notifications.text);
    });

    it('should hide message node by default', () => {
      expect(node.querySelector('.alert')).to.have.class('hidden');
    });

    it('should validate license on start', () => {
      expect(get.calledWith('/api/license/validate')).to.eql(true);
    });

    it('should display license is valid message after initial license check is resolved', () => {
      Promise.runAll();

      expect(node).to.contain.text('Licensed for schrottis inn.');
      expect(node.querySelector('.alert')).not.to.have.class('hidden');
    });

    it('should store and validate new license on form submit', () => {
      post.returns(Promise.resolve('dd'));

      node.querySelector('textarea').value = license;

      triggerEvent({
        node,
        selector: 'form',
        eventName: 'submit'
      });

      expect(post.calledWith('/api/license/validate-and-store', license)).to.eql(true);
    });

    it('should display error when storing new license fails', () => {
      const errorMessage = 'Pay us more monies!';

      post.returns(
        Promise.reject({
          json: sinon.stub().returns(Promise.resolve({
            errorMessage
          }))
        })
      );

      Promise.runAll(); // run initial successful promise

      node.querySelector('textarea').value = license;

      triggerEvent({
        node,
        selector: 'form',
        eventName: 'submit'
      });

      Promise.runAll();

      expect(node.querySelector('.alert')).to.contain.text(errorMessage);
      expect(node.querySelector('.alert')).not.to.have.class('hidden');
    });
  });
});
