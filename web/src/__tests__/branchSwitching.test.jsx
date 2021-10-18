import React from 'react';
import { Provider } from 'react-redux';
import { shallow } from 'enzyme';
import renderer from 'react-test-renderer';
import MDropdown from 'components/ui/MDropdown';
import { MemoryRouter } from 'react-router-dom';
import { RepoFeatures } from '../components/repoFeatures';
import { projectsArrayMock, branchesMock } from '../testData';
import { storeFactory } from '../functions/testUtils';

const setup = () => shallow(
  <RepoFeatures
    projects={projectsArrayMock.projects}
    branch="master"
    branches={branchesMock}
    path=""
    projectId={projectsArrayMock.projects.selectedProject.gid}
    searchableType={projectsArrayMock.projects.selectedProject.searchableType}
    updateLastCommit={() => {}}
  />,
);

xdescribe('There should render', () => {
  let wrapper;
  beforeEach(() => {
    wrapper = setup();
  });
  it('renders without crashing given the required props', () => {
    const buttonsContainer = wrapper.find('#repo-features');
    expect(buttonsContainer).toHaveLength(1);
  });
});

xdescribe('Dropdown appears on button click', () => {
  let wrapper;
  beforeEach(() => {
    wrapper = setup();
  });
  it('renders without crashing given the required props', () => {
    wrapper
      .find(MDropdown)
      .first()
      .dive()
      .find('.m-dropdown-button button')
      .simulate('click');

    expect(
      wrapper
        .find(MDropdown)
        .first()
        .dive()
        .find('.m-dropdown-list-container'),
    ).toHaveLength(1);
  });
});

