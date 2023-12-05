import Head from "next/head";
import { Fragment, useEffect } from "react";
import { Layout as DashboardLayout } from "src/layouts/dashboard/layout";
import {
  Box,
  Button,
  Container,
  Stack,
  Typography,
  Card,
  InputAdornment,
  OutlinedInput,
  SvgIcon,
} from "@mui/material";
import MagnifyingGlassIcon from "@heroicons/react/24/solid/MagnifyingGlassIcon";
import Item from "../components/Item";
import { FallingLines } from 'react-loader-spinner';
import { useState } from "react";
import axios from "axios";


const Page = () => {
  const [searchResults, setSearchResults] = useState([]);
  const [searchString, setSearchString] = useState("");
  const [currentPage, setCurrentPage] = useState(1);

  const [isLoading, setIsLoading] = useState(false);
  const [totalResults, setTotalResults] = useState(0);
  const [newSearch, setNewSearch] = useState(0);

  useEffect(() => {
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const searchInputChangeHandler = (event) => {
    setSearchString(event.target.value);
  };


  const onKeyUp = (event) => {
    if (event.charCode === 13) {
      clear();
      setNewSearch(searchString);
    }
  }


  const clear = () => {
    setCurrentPage(1);
    setTotalResults(0);
    setSearchResults([]);
    setNewSearch("");
  }

  const handleClear = (event) => {
    setSearchString((prev) => "");
    setNewSearch("");
    clear();
  }

  const handleSearch = () => {
    clear();
    setNewSearch(searchString);
  }

  const handleScroll = () => {
    const { scrollTop, clientHeight, scrollHeight } = document.documentElement;
    if (scrollTop + clientHeight >= scrollHeight - 20) {
      setCurrentPage((newVal) => newVal + 1);
    }
  };

  useEffect(() => {
    // Debouce the search by 500ms
    if(searchString != "") {
        setTimeout(() => search(), 500);
    }
  }, [currentPage]);


  useEffect(() => {
    // Debouce the search by 500ms
    if(newSearch != "" && searchResults.length == 0) {
        search();
    }
  }, [newSearch, searchResults]);



  function search() {
    setIsLoading(true);

    // replace with the real search API
    // fetch the whole data

    const serch_api = `http://localhost:8081/api/search`;
    axios({
      // Endpoint to send files
      url: serch_api,
      method: "GET",
      params: {
          query: searchString,
          page: currentPage,
      }
    })
      // Handle the response from backend here
      .then((res) => {
        setTotalResults(res?.data?.total ?? 0);
        setSearchResults([...(currentPage >= 1 ? searchResults : []), ...res?.data?.results]);
        setIsLoading(false);
        setNewSearch("");
      })

      // Catch errors if any
      .catch((err) => {});
  };

  return (
    <Fragment>
      <Head>
        <title>Search | Devias Kit</title>
      </Head>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          py: 8,
        }}
      >
        <Container maxWidth="xl">
          <Stack spacing={3}>
            <Stack direction="row" justifyContent="space-between" spacing={4}>
              <Stack spacing={1}>
                <Typography variant="h4">Search</Typography>
              </Stack>
            </Stack>
            <Card sx={{ p: 2 }}>
              <OutlinedInput
                value={searchString}
                defaultValue=""
                fullWidth
                placeholder="Type to search information from crawler"
                onChange={searchInputChangeHandler}
                onKeyPress={onKeyUp}
                startAdornment={
                  <InputAdornment position="start">
                    <SvgIcon color="action" fontSize="small">
                      <MagnifyingGlassIcon />
                    </SvgIcon>
                  </InputAdornment>
                }
                sx={{ maxWidth: 500 }}
              />
              <Button variant="contained" style={{ margin: "10px" }} onClick={() => handleSearch()}>
                Search
              </Button>
              <Button variant="contained" style={{ margin: "10px", backgroundColor: "red" }} onClick={handleClear}>
                Clear
              </Button>
            </Card>
          </Stack>
        </Container>
        {isLoading && <div style={{color: "black", position: "fixed", left: "calc(50% - 80px)", top: "calc(50% - 80px)", backgroundColor: "white",
          borderRadius: "1rem",
          boxShadow: "grey 0 0 23px -1px",
          padding: "1rem"}}
        >
          <FallingLines
            color="#6466f1"
            width="100"
            visible={true}
            ariaLabel='falling-lines-loading'
          />
        </div>}
        {<Container maxWidth="xl">
          {totalResults > 0 && <p>Total Results: {totalResults}</p>}
          {(searchResults ?? []).map((item) => (
            <Item
              key={item.id}
              id={item.id}
              url={item?.url}
              text={item?.text}
              pagerank={item?.pagerank}
              cosineSimiliarty={item?.cosineSimiliarty}
              score={item?.score}
            />
          ))}
          {(searchResults ?? []).length === 0 && <div>No results, please enter search keywords.</div>}
        </Container>}
      </Box>
    </Fragment>
  );
};

Page.getLayout = (page) => <DashboardLayout>{page}</DashboardLayout>;

export default Page;
