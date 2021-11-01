import React from 'react';
import { string } from 'prop-types';
import ProjectHelp from 'components/views/DetailedProjectView/ProjectHelp';
import AuthWrapper from 'components/AuthWrapper';
import './EmptyProject.scss';

const EmptyProject = ({ httpUrlToRepo, searchableType }) => (
  <>
    <div id="empty-project">
      <h4 style={{ fontSize: '1.3125rem', color: '#2e2e2e', marginBottom: '0.5rem' }}>The repository for this project is empty</h4>
      <AuthWrapper norender minRole={30}>
        <ProjectHelp id={searchableType} />
      </AuthWrapper>
    </div>
    <div className="empty-wrapper" style={{ marginTop: '3em' }}>
      <h3 style={{
        margin: '12px 0', lineHeight: '1.3', fontSize: '1.25em', fontWeight: '600',
      }}
      >
        Command line instructions
      </h3>
      <p>You can also upload existing files from your computer using the instructions below.</p>
      <h5>Git global setup</h5>
      <div style={{ marginBottom: '1em' }}>
        <fieldset>
          <pre className="bg-light">
            <p>
              git config --global user.name &quot;$MLReef User Name&quot;
            </p>
            <p>
              git config --global user.email &quot;$MLReef User Email&quot;
            </p>
          </pre>
        </fieldset>
        <fieldset>
          <h5>Create a new repository</h5>
          <pre className="bg-light">
            git clone
            <span>{` ${httpUrlToRepo}`}</span>
            <p>cd test</p>
            <p>touch README.md</p>
            <p>git add README.md</p>
            <p>git commit -m &quot;add README&quot;</p>
          </pre>
        </fieldset>
        <fieldset>
          <h5>Push an existing folder</h5>
          <pre className="bg-light">
            cd existing_folder
            <p>git init</p>
            <p>
              git remote add origin
              <span>{` ${httpUrlToRepo}`}</span>
            </p>
            <p>git add .</p>
            <p>git commit -m &quot;Initial commit&quot;</p>
          </pre>
        </fieldset>
        <fieldset>
          <h5>Push an existing Git repository</h5>
          <pre className="bg-light">
            cd existing_repo
            <p>git remote rename origin old-origin</p>
            <p>
              git remote add origin
              <span>{` ${httpUrlToRepo}`}</span>
            </p>
          </pre>
        </fieldset>
      </div>
    </div>
  </>
);

EmptyProject.propTypes = {
  httpUrlToRepo: string.isRequired,
  searchableType: string.isRequired,
};

export default EmptyProject;
