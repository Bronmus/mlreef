import React, {
  useCallback,
  useContext, useEffect, useRef, useState,
} from 'react';
import { useParams } from 'react-router-dom';
import { toastr } from 'react-redux-toastr';
import { bindActionCreators } from 'redux';
import {
  bool, func, shape,
} from 'prop-types';
import { connect } from 'react-redux';
import MBricksWall from 'components/ui/MBricksWall';
import * as userActions from 'store/actions/userActions';
import iconGrey from 'images/icon_grey-01.png';
import MProjectCard from 'components/ui/MProjectCard';
import MScrollableSection from 'components/ui/MScrollableSection/MScrollableSection';
import dashboardActions from './dashBoardActions';
import { DashboardContext } from './DashboardContext';
import useEffectNoFirstRender from 'customHooks/useEffectNoFirstRender';

const ProjectsArraySection = (props) => {
  const {
    actions, isLoading,
  } = props;
  const { classification1, classification2, repoName } = useParams();
  const class1 = classification1 || 'public';
  const class2 = classification2 || 'data_project';
  const [projects, setProjects] = useState([]);
  const scrolling = useRef(false);
  const page = useRef(0);
  const isLast = useRef(false);
  const [{
    selectedDataTypes, minimumStars, publishState, sorting,
  }, dispatch] = useContext(DashboardContext);

  useEffectNoFirstRender(() => {
    if (classification2 === 'data_project') {
      dispatch({ type: 'SET_PUBLISH_STATE', payload: -1 });
    }
  }, [classification2]);

  const fetch = (p) => dashboardActions.getProjects(
    class2,
    class1,
    selectedDataTypes,
    minimumStars,
    publishState,
    repoName,
    sorting,
    p,
    10,
  ).then((res) => {
    scrolling.current = false;
    isLast.current = res.last;
    setProjects(
      res.first
        ? res.projects
        : [...projects, ...res.projects],
    );
  })
    .catch((err) => {
      toastr.error('Error', err.message);
    })
    .finally(() => actions.setIsLoading(false));

  const executeFetch = useCallback(() => {
    scrolling.current = true;
    page.current = 0;
    fetch(page.current);
  },
  [
    classification1,
    classification2,
    selectedDataTypes,
    repoName,
    minimumStars,
    publishState,
    sorting,
  ]);

  useEffect(() => {
    executeFetch();
  }, [executeFetch]);

  const handleOnScrollDown = () => {
    if (scrolling.current) return;
    if (isLast.current) return;
    page.current += 1;

    scrolling.current = true;
    fetch(page.current);
  };

/*   const sortedProjects = useMemo(
    () => projects.sort(comparingFunctions[sorting]),
    [projects, sorting],
  ); */

  return (
    <div className="dashboard-v2-content-projects">
      <div className="dashboard-v2-content-projects-margin-div">
        {projects.length > 0 && !isLoading ? (
          <MScrollableSection
            className="w-100"
            handleOnScrollDown={handleOnScrollDown}
          >
            <MBricksWall
              animated
              bricks={projects.map((proj) => (
                <MProjectCard
                  key={`proj-${proj.gitlabNamespace}-${proj.slug}-${proj.id}`}
                  slug={proj.slug}
                  title={proj.name}
                  description={proj.description}
                  starCount={proj.starsCount || 0}
                  forkCount={proj.forksCount || 0}
                  experimentsCount={proj.experiments?.length}
                  namespace={proj.gitlabNamespace}
                  inputDataTypes={proj.inputDataTypes}
                  users={proj.members}
                  visibility={proj.visibilityScope}
                  owner={proj.ownerId === ''}
                  published={proj.published}
                  classification={classification2}
                  coverUrl={proj.coverUrl}
                />
              ))}
            />
          </MScrollableSection>
        ) : (
          <div className="d-flex noelement-found-div">
            <img src={iconGrey} alt="" style={{ maxHeight: '100px' }} />
            <p>No projects found</p>
          </div>
        )}
      </div>
    </div>
  );
};

function mapStateToProps(state) {
  return {
    isLoading: state.globalMarker?.isLoading,
  };
}

function mapDispatchToProps(dispatch) {
  return {
    actions: bindActionCreators({
      ...userActions,
    }, dispatch),
  };
}

ProjectsArraySection.propTypes = {
  isLoading: bool.isRequired,
  actions: shape({ setIsLoading: func }).isRequired,
};

export default connect(mapStateToProps, mapDispatchToProps)(ProjectsArraySection);
